package com.chatguard.domain.moderation.queue;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import com.chatguard.domain.moderation.service.TextModerationService;

import java.nio.charset.StandardCharsets;

class BannedWordsMessageListenerTest {

    private TextModerationService textModerationService;
    private BannedWordsMessageListener listener;

    @BeforeEach
    void setUp() {
        textModerationService = mock(TextModerationService.class);
        listener = new BannedWordsMessageListener(textModerationService);
    }

    @Test
    void Redis채널로_무효화이벤트_메시지_수신시_refreshCache를_수행한다() {
        // Given
        Message message = mock(Message.class);
        when(message.getChannel()).thenReturn("config:banned-words".getBytes(StandardCharsets.UTF_8));
        when(message.getBody()).thenReturn("refresh".getBytes(StandardCharsets.UTF_8));

        // When
        listener.onMessage(message, null);

        // Then
        verify(textModerationService, times(1)).refreshCache();
    }
}
