package com.chatguard.global.config;

import com.chatguard.domain.chat.queue.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public MessageListenerAdapter chatMessageListener(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onChatMessage");
    }

    @Bean
    public MessageListenerAdapter moderationResultListener(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onModerationResult");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter chatMessageListener,
            MessageListenerAdapter moderationResultListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatMessageListener, new PatternTopic("chat:room:*"));
        container.addMessageListener(moderationResultListener, new PatternTopic("mod:result:*"));
        return container;
    }
}
