package com.chatguard.domain.chat.service;

import com.chatguard.domain.chat.dto.ChatMessageDto;
import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.dto.MessageDto;
import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import com.chatguard.domain.chat.queue.ModerationQueueProducer;
import com.chatguard.domain.chat.repository.MessageRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final String CHAT_CHANNEL_PREFIX = "chat:room:";
    private static final Set<String> KEYWORD_FILTER = Set.of("욕설1", "욕설2", "금칙어");

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final ModerationQueueProducer moderationQueueProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void sendMessage(Long userId, ChatSendDto dto) {
        Room room = roomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        String content = dto.getContent();
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

        messageRepository.save(message);

        if (!blocked) {
            publish(room.getId(), ChatMessageDto.from(message));
            moderationQueueProducer.enqueue(message.getId(), room.getId(), content);
        }
    }

    public List<MessageDto> getHistory(Long roomId, String beforeId, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<Message> messages = (beforeId != null && !beforeId.isBlank())
                ? messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, page)
                : messageRepository.findByRoomIdOrderByIdDesc(roomId, page);

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
}
