package com.chatguard.domain.chat.queue;

import com.chatguard.domain.chat.dto.ChatHideDto;
import com.chatguard.domain.chat.entity.MessageStatus;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.chat.ws.ChatRoomSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber {

    private final ChatRoomSessionRegistry registry;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public void onChatMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            Long roomId = root.path("payload").path("room_id").asLong();
            registry.broadcast(roomId, payload);
        } catch (Exception e) {
            log.error("Failed to broadcast chat message", e);
        }
    }

    public void onModerationResult(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String messageId = node.get("messageId").asText();
            String action = node.get("action").asText();
            Long roomId = node.get("roomId").asLong();

            MessageStatus newStatus = "delete".equals(action) ? MessageStatus.DELETED : MessageStatus.BLURRED;
            messageRepository.findById(messageId).ifPresent(msg -> {
                msg.changeStatus(newStatus);
                messageRepository.save(msg);
            });

            String hidePayload = objectMapper.writeValueAsString(ChatHideDto.of(messageId, action));
            registry.broadcast(roomId, hidePayload);
        } catch (Exception e) {
            log.error("Failed to process moderation result", e);
        }
    }
}
