package com.chatguard.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.entity.ModerationLog;
import com.chatguard.domain.chat.entity.Stage;
import com.chatguard.domain.chat.entity.Verdict;
import com.chatguard.domain.chat.queue.ModerationQueueProducer;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.chat.service.ChatService.SendMessageResult;
import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.room.repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

class ChatServiceSendMessageTest {

    private MessageRepository messageRepository;
    private ModerationLogService moderationLogService;
    private RoomRepository roomRepository;
    private ModerationQueueProducer moderationQueueProducer;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        moderationLogService = mock(ModerationLogService.class);
        roomRepository = mock(RoomRepository.class);
        moderationQueueProducer = mock(ModerationQueueProducer.class);

        chatService = new ChatService(
            messageRepository,
            moderationLogService,
            roomRepository,
            moderationQueueProducer,
            mock(StringRedisTemplate.class),
            new ObjectMapper(),
            mock(EntityManager.class)
        );

        when(roomRepository.findById(1L)).thenReturn(Optional.of(mock(Room.class)));
    }

    @Test
    void 키워드_차단시_moderation_log에_26자_ULID를_기록하고_메시지는_저장하지_않는다() {
        // P1-1: ULID는 키워드 검열 전에 1회 발급되어 차단 로그에 그대로 기록되어야 한다(별도 발급 금지).
        SendMessageResult result = chatService.sendMessage(7L, "viewer7",
            new ChatSendDto(1L, "이건 금칙어 포함 메시지"));

        assertThat(result).isEqualTo(SendMessageResult.BLOCKED_KEYWORD);

        ArgumentCaptor<ModerationLog> captor = ArgumentCaptor.forClass(ModerationLog.class);
        verify(moderationLogService).saveInNewTransaction(captor.capture());
        ModerationLog logged = captor.getValue();

        assertThat(logged.getStage()).isEqualTo(Stage.KEYWORD);
        assertThat(logged.getVerdict()).isEqualTo(Verdict.BLOCK);
        assertThat(logged.getMessageId())
            .hasSize(26)
            .matches("[0-9A-HJKMNP-TV-Z]{26}"); // Crockford base32 (ULID)

        // 차단 시 messages 미저장 + 큐 미적재(D30)
        verify(messageRepository, never()).save(any());
        verify(moderationQueueProducer, never()).enqueue(any(), any(), any());
    }
}
