package com.chatguard.domain.chat.queue;

import com.chatguard.domain.admin.service.RoomFreezeService;
import com.chatguard.domain.chat.ws.ChatRoomSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final RoomFreezeService roomFreezeService;

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

            // room.freeze 이벤트는 로컬 캐시에도 반영 (A-4 step 7: 전 파드 로컬 플래그 갱신)
            JsonNode root = objectMapper.readTree(payload);
            if ("room.freeze".equals(root.path("type").asText())) {
                boolean frozen = root.path("payload").path("frozen").asBoolean(false);
                roomFreezeService.updateLocalCache(roomId, frozen);
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
