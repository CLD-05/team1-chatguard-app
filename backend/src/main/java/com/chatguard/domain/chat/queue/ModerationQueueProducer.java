package com.chatguard.domain.chat.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationQueueProducer {

    private static final String QUEUE_KEY = "mod:queue";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void enqueue(String messageId, Long roomId, String content) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "messageId", messageId,
                    "roomId", roomId,
                    "content", content
            ));
            redisTemplate.opsForList().rightPush(QUEUE_KEY, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to enqueue moderation task for messageId={}", messageId, e);
        }
    }
}
