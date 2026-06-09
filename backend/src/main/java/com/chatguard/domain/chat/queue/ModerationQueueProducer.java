package com.chatguard.domain.chat.queue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

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

    public void enqueue(String messageId, Long roomId, Long userId, String content) {
        try {
            String payload = objectMapper.writeValueAsString(new ModerationJob(messageId, roomId, userId, content));
            redisTemplate.opsForList().leftPush(queueName, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize moderation job", e);
        }
    }

    private record ModerationJob(
        @JsonProperty("message_id")
        String messageId,
        @JsonProperty("room_id")
        Long roomId,
        @JsonProperty("user_id")
        Long userId,
        String content
    ) {
    }

}
