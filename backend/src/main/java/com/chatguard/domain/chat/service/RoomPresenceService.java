package com.chatguard.domain.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomPresenceService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ROOM_CHANNEL_PREFIX:room:}")
    private String roomChannelPrefix;

    public void join(Long roomId, Long userId, String displayName) {
        redisTemplate.opsForHash().put(membersKey(roomId), String.valueOf(userId), displayName);
        publishPresenceUpdate(roomId);
    }

    public void leave(Long roomId, Long userId) {
        Long deleted = redisTemplate.opsForHash().delete(membersKey(roomId), String.valueOf(userId));
        if (deleted != null && deleted > 0) {
            publishPresenceUpdate(roomId);
        }
    }

    public Map<String, Object> getSnapshot(Long roomId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(membersKey(roomId));
        List<Map<String, Object>> members = raw.entrySet().stream()
            .map(e -> Map.<String, Object>of(
                "user_id", Long.parseLong((String) e.getKey()),
                "display_name", e.getValue()
            ))
            .collect(Collectors.toList());
        return Map.of("room_id", roomId, "count", members.size(), "members", members);
    }

    private void publishPresenceUpdate(Long roomId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "type", "presence.update",
                "payload", getSnapshot(roomId)
            ));
            redisTemplate.convertAndSend(roomChannelPrefix + roomId, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize presence.update for roomId={}", roomId, e);
        } catch (Exception e) {
            log.error("Failed to publish presence.update to Redis for roomId={}", roomId, e);
        }
    }

    private String membersKey(Long roomId) {
        return roomChannelPrefix + roomId + ":members";
    }
}
