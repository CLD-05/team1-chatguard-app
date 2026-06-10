package com.chatguard.domain.chat.queue;

import com.chatguard.domain.chat.ws.ChatRoomSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final ChatRoomSessionRegistry registry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);

            Long roomId = parseRoomId(channel);
            if (roomId == null) {
                log.warn("Cannot parse roomId from channel={}", channel);
                return;
            }
            registry.broadcast(roomId, payload);
        } catch (Exception e) {
            log.error("Failed to forward room message", e);
        }
    }

    private Long parseRoomId(String channel) {
        if (channel == null) return null;
        String[] parts = channel.split(":");
        if (parts.length < 2) return null;
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
