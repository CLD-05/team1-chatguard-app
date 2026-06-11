package com.chatguard.domain.chat.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationQueueProducer {

    private static final String QUEUE_KEY = "mod:queue";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void enqueue(String messageId, Long roomId, String content) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "message_id", messageId,
                "room_id", roomId,
                "content", content,
                "enqueued_at", Instant.now().toString()
        ));
        redisTemplate.opsForList().rightPush(QUEUE_KEY, payload);
    }
}
