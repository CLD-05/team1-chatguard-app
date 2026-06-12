package com.chatguard.domain.chat.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import com.chatguard.domain.moderation.entity.ModerationLog;
import com.chatguard.domain.moderation.entity.Stage;
import com.chatguard.domain.moderation.entity.Verdict;
import com.chatguard.domain.moderation.service.ModerationLogService;
import com.chatguard.domain.moderation.service.TextModerationService;
import com.chatguard.domain.chat.queue.ModerationQueueProducer;
import com.chatguard.domain.chat.repository.MessageRepository;
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

    private final MessageRepository messageRepository;
    private final ModerationLogService moderationLogService;
    private final TextModerationService textModerationService;
    private final RoomRepository roomRepository;
    private final ModerationQueueProducer moderationQueueProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Value("${ROOM_CHANNEL_PREFIX:room:}")
    private String roomChannelPrefix = "room:";

    @Transactional
    public SendMessageResult sendMessage(Long userId, String displayName, ChatSendDto dto) {
        Long roomId = required(dto.roomId(), "room_id");
        String content = normalizeContent(dto.content());

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        User user = entityManager.getReference(User.class, userId);

        String messageId = UlidGenerator.generate();

        Verdict verdict = textModerationService.judge(content);

        if (verdict == Verdict.BLOCK) {
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