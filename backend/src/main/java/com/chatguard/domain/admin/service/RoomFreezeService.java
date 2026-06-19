package com.chatguard.domain.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomFreezeService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ROOM_CHANNEL_PREFIX:room:}")
    private String roomChannelPrefix;

    private final ConcurrentHashMap<Long, Boolean> localCache = new ConcurrentHashMap<>();

    public void updateLocalCache(Long roomId, boolean frozen) {
        localCache.put(roomId, frozen);
    }

    public void setFrozen(Long roomId, boolean frozen) {
        String key = frozenKey(roomId);
        if (frozen) {
            redisTemplate.opsForValue().set(key, "true");
        } else {
            redisTemplate.delete(key);
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "type", "room.freeze",
                "payload", Map.of("room_id", roomId, "frozen", frozen)
            ));
            redisTemplate.convertAndSend(roomChannelPrefix + roomId, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to publish room.freeze event for roomId={}", roomId, e);
        }
    }

    public boolean isFrozen(Long roomId) {
        Boolean cached = localCache.get(roomId);
        if (cached != null) return cached;
        return "true".equals(redisTemplate.opsForValue().get(frozenKey(roomId)));
    }

    private String frozenKey(Long roomId) {
        return roomChannelPrefix + roomId + ":frozen";
    }
}
