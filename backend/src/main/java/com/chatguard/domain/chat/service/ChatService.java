package com.chatguard.domain.chat.service;

import com.chatguard.domain.chat.dto.ChatMessageDto;
import com.chatguard.domain.chat.dto.ChatHideDto;
import com.chatguard.domain.chat.dto.ModerationResultRequest;
import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import com.chatguard.domain.chat.entity.ModerationLog;
import com.chatguard.domain.chat.entity.Stage;
import com.chatguard.domain.chat.entity.Verdict;
import com.chatguard.domain.chat.queue.ChatEventBroadcaster;
import com.chatguard.domain.chat.queue.ModerationQueueProducer;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.chat.repository.ModerationLogRepository;
import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.room.repository.RoomRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.util.UlidGenerator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatService {
    private static final int DEFAULT_LIMIT = 50;

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final ModerationQueueProducer moderationQueueProducer;
    private final ChatEventBroadcaster chatEventBroadcaster;

    public ChatService(
        MessageRepository messageRepository,
        RoomRepository roomRepository,
        UserRepository userRepository,
        ModerationLogRepository moderationLogRepository,
        ModerationQueueProducer moderationQueueProducer,
        ChatEventBroadcaster chatEventBroadcaster
    ) {
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.moderationQueueProducer = moderationQueueProducer;
        this.chatEventBroadcaster = chatEventBroadcaster;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(Long roomId, String before, int limit) {
        int size = limit > 0 ? Math.min(limit, DEFAULT_LIMIT) : DEFAULT_LIMIT;
        List<Message> messages = before == null || before.isBlank()
            ? messageRepository.findByRoomIdOrderByIdDesc(roomId, PageRequest.of(0, size))
            : messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, before, PageRequest.of(0, size));

        List<Message> ordered = new ArrayList<>(messages);
        Collections.reverse(ordered);
        return ordered.stream()
            .map(ChatMessageDto::from)
            .toList();
    }

    @Transactional
    public ChatMessageDto sendMessage(Long roomId, Long userId, String content) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("room not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("user not found"));
        String normalizedContent = normalizeContent(content);

        Message message = Message.builder()
            .id(UlidGenerator.generate())
            .room(room)
            .user(user)
            .content(normalizedContent)
            .createdAt(LocalDateTime.now())
            .build();

        Message saved = messageRepository.save(message);
        moderationQueueProducer.enqueue(saved.getId(), room.getId(), user.getId(), saved.getContent());
        ChatMessageDto response = ChatMessageDto.from(saved);
        chatEventBroadcaster.broadcast(room.getId(), "chat.message", response);
        return response;
    }

    @Transactional
    public ChatMessageDto applyModerationResult(ModerationResultRequest request) {
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

        ChatMessageDto response = ChatMessageDto.from(message);
        if (status == MessageStatus.BLURRED || status == MessageStatus.DELETED) {
            chatEventBroadcaster.broadcast(
                message.getRoom().getId(),
                "moderation.hide",
                new ChatHideDto(message.getId(), actionFromStatus(status), message.getRoom().getId())
            );
        }
        return response;
    }

    private String normalizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("content is required");
        }
        return content.trim();
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
