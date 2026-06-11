package com.chatguard.domain.chat.service;

import com.chatguard.domain.chat.dto.ChatHideDto;
import com.chatguard.domain.chat.dto.ChatMessageDto;
import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.dto.MessageDto;
import com.chatguard.domain.chat.dto.ModerationResultRequest;
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
    public void sendMessage(Long userId, ChatSendDto dto) {
        Long roomId = required(dto.roomId(), "room_id");
        String content = normalizeContent(dto.content());

        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        boolean blocked = KEYWORD_FILTER.stream().anyMatch(content::contains);
        Message message = Message.builder()
            .id(UlidGenerator.generate())
            .room(room)
            .user(user)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();

        if (blocked) {
            message.changeStatus(MessageStatus.DELETED);
        }

        Message saved = messageRepository.save(message);

        if (blocked) {
            moderationLogRepository.save(ModerationLog.builder()
                .messageId(saved.getId())
                .stage(Stage.KEYWORD)
                .verdict(Verdict.BLOCK)
                .checkedAt(LocalDateTime.now())
                .build());
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(roomId, ChatMessageDto.from(saved));
                moderationQueueProducer.enqueue(saved.getId(), roomId, userId, saved.getContent());
            }
        });
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

    @Transactional
    public MessageDto applyModerationResult(ModerationResultRequest request) {
        Message message = messageRepository.findById(required(request.messageId(), "message_id"))
            .orElseThrow(() -> new IllegalArgumentException("message not found"));

        MessageStatus status = statusFromAction(request.action());
        message.changeStatus(status);

        moderationLogRepository.save(ModerationLog.builder()
            .messageId(message.getId())
            .stage(Stage.AI)
            .verdict(normalizeVerdict(request.verdict(), status))
            .score(request.score())
            .modelVersion(defaultString(request.modelVersion(), "unknown"))
            .reason(defaultString(request.reason(), ""))
            .checkedAt(LocalDateTime.now())
            .build());

        MessageDto response = MessageDto.from(message);
        if (status == MessageStatus.BLURRED || status == MessageStatus.DELETED) {
            Long roomId = message.getRoom().getId();
            String action = actionFromStatus(status);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(roomId, ChatHideDto.of(message.getId(), action));
                }
            });
        }
        return response;
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
        return content.trim();
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

    private MessageStatus statusFromAction(String action) {
        String normalizedAction = required(action, "action").toLowerCase();
        if ("delete".equals(normalizedAction)) {
            return MessageStatus.DELETED;
        }
        if ("blur".equals(normalizedAction)) {
            return MessageStatus.BLURRED;
        }
        if ("pass".equals(normalizedAction)) {
            return MessageStatus.VISIBLE;
        }
        throw new IllegalArgumentException("action must be one of pass, blur, delete");
    }

    private String actionFromStatus(MessageStatus status) {
        if (status == MessageStatus.DELETED) {
            return "delete";
        }
        if (status == MessageStatus.BLURRED) {
            return "blur";
        }
        return "pass";
    }

    private Verdict normalizeVerdict(String verdict, MessageStatus status) {
        if (verdict != null && !verdict.trim().isEmpty()) {
            try {
                return Verdict.valueOf(verdict.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("verdict must be one of PASS, BLOCK");
            }
        }
        return status == MessageStatus.VISIBLE ? Verdict.PASS : Verdict.BLOCK;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
