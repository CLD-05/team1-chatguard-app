package com.chatguard.domain.chat.service;

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
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.error.CustomException;
import com.chatguard.global.error.ErrorCode;
import com.chatguard.global.util.UlidGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {
    private static final String CHAT_CHANNEL_PREFIX = "room:";
    private static final Set<String> KEYWORD_FILTER = Set.of("욕설1", "욕설2", "금칙어");

    private final MessageRepository messageRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final ModerationQueueProducer moderationQueueProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public SendMessageResult sendMessage(Long userId, ChatSendDto dto) {
        Long roomId = required(dto.roomId(), "room_id");
        String content = normalizeContent(dto.content());

        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (KEYWORD_FILTER.stream().anyMatch(content::contains)) {
            moderationLogRepository.save(ModerationLog.builder()
                .messageId(UlidGenerator.generate())
                .stage(Stage.KEYWORD)
                .verdict(Verdict.BLOCK)
                .content(content)
                .checkedAt(LocalDateTime.now())
                .build());
            return SendMessageResult.BLOCKED_KEYWORD;
        }

        Message saved = messageRepository.save(Message.builder()
            .id(UlidGenerator.generate())
            .room(room)
            .user(user)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                moderationQueueProducer.enqueue(saved.getId(), roomId, saved.getContent());
                publish(roomId, ChatMessageDto.from(saved));
            }
        });
        return SendMessageResult.SENT;
    }

    public List<MessageDto> getHistory(Long roomId, String beforeId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 50)));
        List<Message> messages = beforeId != null && !beforeId.isBlank()
            ? messageRepository.findByRoomIdAndIdLessThanAndStatusNotOrderByIdAsc(
                roomId,
                beforeId,
                MessageStatus.DELETED,
                page
            )
            : messageRepository.findByRoomIdAndStatusNotOrderByIdAsc(roomId, MessageStatus.DELETED, page);

        return messages.stream().map(MessageDto::from).toList();
    }

    private void publish(Long roomId, Object dto) {
        try {
            String payload = objectMapper.writeValueAsString(dto);
            redisTemplate.convertAndSend(CHAT_CHANNEL_PREFIX + roomId, payload);
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

    public enum SendMessageResult {
        SENT,
        BLOCKED_KEYWORD
    }
}
