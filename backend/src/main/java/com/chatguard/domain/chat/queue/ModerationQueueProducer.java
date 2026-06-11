package com.chatguard.domain.chat.queue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class ModerationQueueProducer {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;

    public ModerationQueueProducer(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        @Value("${moderation.queue-name:mod:queue}") String queueName
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
    }

    public void enqueue(String messageId, Long roomId, String content) {
        try {
            String payload = objectMapper.writeValueAsString(new ModerationJob(messageId, roomId, content, Instant.now().toString()));
            redisTemplate.opsForList().leftPush(queueName, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to enqueue moderation task for messageId={}", messageId, e);
            throw new IllegalStateException("Failed to serialize moderation job", e);
        }
    }

    private record ModerationJob(
        @JsonProperty("message_id")
        String messageId,
        @JsonProperty("room_id")
        Long roomId,
        String content,
        @JsonProperty("enqueued_at")
        String enqueuedAt
    ) {
    }
}
