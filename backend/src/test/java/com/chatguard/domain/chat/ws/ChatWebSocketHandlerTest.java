package com.chatguard.domain.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.chatguard.domain.chat.service.ChatService;
import com.chatguard.domain.moderation.service.TextModerationService;
import com.chatguard.domain.moderation.service.ModerationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;

class ChatWebSocketHandlerTest {

    private ChatService chatService;
    private ChatRoomSessionRegistry registry;
    private TextModerationService textModerationService;
    private ModerationLogService moderationLogService;
    private ObjectMapper objectMapper;
    private ChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        registry = mock(ChatRoomSessionRegistry.class);
        textModerationService = mock(TextModerationService.class);
        moderationLogService = mock(ModerationLogService.class);
        objectMapper = new ObjectMapper();
        
        handler = new ChatWebSocketHandler(
            chatService,
            registry,
            objectMapper,
            textModerationService,
            moderationLogService
        );
    }

    @Test
    void 금칙어_메시지가_들어오면_선제_차단하고_에러프레임을_보내며_chatService는_호출하지_않는다() throws Exception {
        // Given
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", 100L);
        attributes.put("roomId", 1L);
        attributes.put("displayName", "UserA");
        when(session.getAttributes()).thenReturn(attributes);

        // 금칙어가 들어간 payload 정의
        String payload = "{\"type\":\"chat.send\",\"payload\":{\"room_id\":1,\"content\":\"이것은 시발 메시지\"}}";
        TextMessage textMessage = new TextMessage(payload);

        // 검열 모킹
        when(textModerationService.judge("이것은 시발 메시지")).thenReturn(true);

        // When
        handler.handleTextMessage(session, textMessage);

        // Then: 1) chatService.sendMessage는 절대로 호출되지 않음 (Drop)
        verify(chatService, never()).sendMessage(any(), any(), any());

        // Then: 2) moderationLogService.saveInNewTransaction가 1회 실행되어 차단 로그가 남음
        verify(moderationLogService, times(1)).saveInNewTransaction(any());

        // Then: 3) 사용자 세션에 MESSAGE_BLOCKED 에러 프레임이 실시간 전송됨
        ArgumentCaptor<TextMessage> textMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(textMessageCaptor.capture());
        
        String errorResponseJson = textMessageCaptor.getValue().getPayload();
        assertThat(errorResponseJson)
            .contains("\"type\":\"error\"")
            .contains("\"code\":\"MESSAGE_BLOCKED\"")
            .contains("\"message\":\"금칙어가 포함되어 있습니다.\"");
            
        // Then: 4) 웹소켓 세션 close()는 호출되지 않아 연결이 유지됨
        verify(session, never()).close();
    }

    @Test
    void 정상_메시지인_경우_검열을_통과하고_chatService로_위임된다() throws Exception {
        // Given
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", 100L);
        attributes.put("roomId", 1L);
        attributes.put("displayName", "UserA");
        when(session.getAttributes()).thenReturn(attributes);

        String payload = "{\"type\":\"chat.send\",\"payload\":{\"room_id\":1,\"content\":\"안녕하세요 정상 메시지\"}}";
        TextMessage textMessage = new TextMessage(payload);

        when(textModerationService.judge("안녕하세요 정상 메시지")).thenReturn(false);

        // When
        handler.handleTextMessage(session, textMessage);

        // Then: 1) chatService.sendMessage가 정상적으로 위임 호출됨
        verify(chatService, times(1)).sendMessage(eq(100L), eq("UserA"), any());
        
        // Then: 2) moderationLogService는 호출되지 않음
        verify(moderationLogService, never()).saveInNewTransaction(any());
        
        // Then: 3) 세션 오류 응답이 전송되지 않음
        verify(session, never()).sendMessage(any());
    }
}
