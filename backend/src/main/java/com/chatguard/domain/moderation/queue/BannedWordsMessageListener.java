package com.chatguard.domain.moderation.queue;

import com.chatguard.domain.moderation.service.TextModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class BannedWordsMessageListener implements MessageListener {

    private final TextModerationService textModerationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("Received Redis pub/sub invalidation event on channel={}: payload={}", channel, body);
        try {
            textModerationService.refreshCache();
        } catch (Exception e) {
            log.error("Failed to refresh banned keywords cache on Redis message received", e);
        }
    }
}
