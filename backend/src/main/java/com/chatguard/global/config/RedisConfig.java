package com.chatguard.global.config;

import com.chatguard.domain.chat.queue.RedisMessageSubscriber;
import com.chatguard.domain.moderation.queue.BannedWordsMessageListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber subscriber,
            BannedWordsMessageListener bannedWordsMessageListener,
            @Value("${ROOM_CHANNEL_PREFIX:room:}") String roomChannelPrefix) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // A-5 env 계약: 구독 패턴도 ROOM_CHANNEL_PREFIX 기반(기본 room:*)으로 발행 측과 정렬한다.
        container.addMessageListener(subscriber, new PatternTopic(roomChannelPrefix + "*"));
        
        // config:banned-words 전역 무효화 채널 리스너 추가
        container.addMessageListener(bannedWordsMessageListener, new ChannelTopic("config:banned-words"));
        return container;
    }
}
