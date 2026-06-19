package com.chatguard.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.chatguard.domain.admin.service.RoomFreezeService;
import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.moderation.entity.ModerationLog;
import com.chatguard.domain.moderation.entity.Stage;
import com.chatguard.domain.moderation.entity.Verdict;
import com.chatguard.domain.moderation.service.ModerationLogService;
import com.chatguard.domain.moderation.service.TextModerationService;
import com.chatguard.domain.chat.queue.ModerationQueueProducer;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.chat.service.ChatService.SendMessageResult;
import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.room.repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;

class ChatServiceSendMessageTest {

    private MessageRepository messageRepository;
    private ModerationLogService moderationLogService;
    private TextModerationService textModerationService;
    private RoomRepository roomRepository;
    private ModerationQueueProducer moderationQueueProducer;
    private RoomFreezeService roomFreezeService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        moderationLogService = mock(ModerationLogService.class);
        textModerationService = mock(TextModerationService.class);
        roomRepository = mock(RoomRepository.class);
        moderationQueueProducer = mock(ModerationQueueProducer.class);
        roomFreezeService = mock(RoomFreezeService.class);

        chatService = new ChatService(
            messageRepository,
            moderationLogService,
            textModerationService,
            roomRepository,
            moderationQueueProducer,
            mock(StringRedisTemplate.class),
            new ObjectMapper(),
            mock(EntityManager.class),
            mock(MeterRegistry.class, RETURNS_DEEP_STUBS),
            roomFreezeService
        );

        when(roomRepository.findById(1L)).thenReturn(Optional.of(mock(Room.class)));
        // 기본값은 PASS로 설정 (차단되지 않음 = false)
        when(textModerationService.judge(any())).thenReturn(false);
        when(roomFreezeService.isFrozen(any())).thenReturn(false);
    }

    @Test
    void 키워드_차단시_moderation_log에_26자_ULID를_기록하고_메시지는_저장하지_않는다() {
        // P1-1: ULID는 키워드 검열 전에 1회 발급되어 차단 로그에 그대로 기록되어야 한다(별도 발급 금지).
        when(textModerationService.judge(any())).thenReturn(true);
        
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

    @Test
    void freeze_상태에서_메시지_전송시_BLOCKED_FROZEN을_반환하고_DB와_큐를_건드리지_않는다() {
        // D45: frozen이면 저장·큐·전파 없이 BLOCKED_FROZEN 반환
        when(roomFreezeService.isFrozen(1L)).thenReturn(true);

        SendMessageResult result = chatService.sendMessage(7L, "viewer7",
            new ChatSendDto(1L, "안녕하세요"));

        assertThat(result).isEqualTo(SendMessageResult.BLOCKED_FROZEN);
        verify(messageRepository, never()).save(any());
        verify(moderationQueueProducer, never()).enqueue(any(), any(), any());
        verify(moderationLogService, never()).saveInNewTransaction(any());
    }
}
