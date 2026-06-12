package com.chatguard.domain.chat.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chatguard.domain.chat.dto.ChatMessageDto;
import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.dto.MessageDto;
import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import com.chatguard.domain.chat.entity.ModerationLog;
import com.chatguard.domain.chat.entity.Stage;
import com.chatguard.domain.chat.entity.Verdict;
import com.chatguard.domain.chat.queue.ModerationQueueProducer;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.chat.repository.ModerationLogRepository;
import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.room.repository.RoomRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.global.error.CustomException;
import com.chatguard.global.error.ErrorCode;
import com.chatguard.global.util.UlidGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {
    private static final Set<String> KEYWORD_FILTER = Set.of("욕설1", "욕설2", "금칙어");

    private final MessageRepository messageRepository;
    private final ModerationLogService moderationLogService;
    private final RoomRepository roomRepository;
    private final ModerationQueueProducer moderationQueueProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    // A-5 env 계약: 채널 prefix는 ROOM_CHANNEL_PREFIX(기본 room:)로 주입한다(하드코딩 금지).
    // 최종 deps는 생성자 주입이지만 이 값만 필드 주입이라 단위 테스트의 생성자 시그니처를 건드리지 않는다.
    @Value("${ROOM_CHANNEL_PREFIX:room:}")
    private String roomChannelPrefix = "room:";

    @Transactional
    public SendMessageResult sendMessage(Long userId, String displayName, ChatSendDto dto) {
        Long roomId = required(dto.roomId(), "room_id");
        String content = normalizeContent(dto.content());

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        User user = entityManager.getReference(User.class, userId);

        // A-4 step1: ULID는 키워드 검열보다 먼저 1회 발급한다.
        // 차단 로그(moderation_logs.message_id)와 저장 메시지(messages.id)가 동일 ULID를 공유해야
        // 동일한 전송 시도의 감사추적이 끊기지 않는다(D25).
        String messageId = UlidGenerator.generate();

        if (KEYWORD_FILTER.stream().anyMatch(content::contains)) {
            moderationLogService.saveInNewTransaction(ModerationLog.builder()
                    .messageId(messageId)
                    .stage(Stage.KEYWORD)
                    .verdict(Verdict.BLOCK)
                    .content(content)
                    .checkedAt(nowUtc())
                    .build());
            return SendMessageResult.BLOCKED_KEYWORD;
        }

        Message saved = messageRepository.save(Message.builder()
                .id(messageId)
                .room(room)
                .user(user)
                .content(content)
                .createdAt(nowUtc())
                .build());

        moderationQueueProducer.enqueue(saved.getId(), roomId, saved.getContent());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(roomId, ChatMessageDto.from(saved, displayName));
            }
        });
        return SendMessageResult.SENT;
    }

    public List<MessageDto> getHistory(Long roomId, String beforeId, int limit) {
        // 미존재 room_id는 404 ROOM_NOT_FOUND로 거부한다(빈 배열 200 금지 — A-3 에러표 / D29).
        if (!roomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 50)));
        List<Message> messages = new ArrayList<>(beforeId != null && !beforeId.isBlank()
                ? messageRepository.findByRoomIdAndIdLessThanAndStatusNotOrderByIdDesc(
                        roomId,
                        beforeId,
                        MessageStatus.DELETED,
                        page)
                : messageRepository.findByRoomIdAndStatusNotOrderByIdDesc(roomId, MessageStatus.DELETED, page));

        // 최신 N건을 id 내림차순으로 조회한 뒤(D27 캐치업 윈도우), 렌더 순서대로 시간 오름차순으로 뒤집어 반환한다.
        Collections.reverse(messages);
        return messages.stream().map(MessageDto::from).toList();
    }

    private void publish(Long roomId, Object dto) {
        try {
            String payload = objectMapper.writeValueAsString(dto);
            redisTemplate.convertAndSend(roomChannelPrefix + roomId, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to publish to Redis", e);
        }
    }

    private String normalizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("content is required");
        }
        String normalized = content.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("content must be 500 characters or less");
        }
        return normalized;
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public enum SendMessageResult {
        SENT,
        BLOCKED_KEYWORD
    }
}