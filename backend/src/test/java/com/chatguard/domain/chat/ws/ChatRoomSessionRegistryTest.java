package com.chatguard.domain.chat.ws;

import com.chatguard.domain.chat.service.RoomPresenceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

@ExtendWith(MockitoExtension.class)
class ChatRoomSessionRegistryTest {

    private ChatRoomSessionRegistry registry;
    private MeterRegistry meterRegistry;

    @Mock
    private RoomPresenceService roomPresenceService;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        registry = new ChatRoomSessionRegistry(meterRegistry, roomPresenceService);
    }

    @Test
    void register_WhenCapReached_ShouldCloseWith1013() throws IOException {
        // 1. Cap을 1로 임시 지정하여 오토스케일 한도 도달 상황 모사
        ReflectionTestUtils.setField(registry, "connectionCap", 1);
        
        when(session1.getId()).thenReturn("sess-1");
        when(session2.getId()).thenReturn("sess-2");

        // 첫 번째 등록은 통과
        registry.register(1L, session1);
        verify(session1, never()).close(any(CloseStatus.class));

        // 두 번째 등록은 Cap 한도 초과로 1013 close 거부 검증 (D50 계약 준수)
        registry.register(1L, session2);
        verify(session2, times(1)).close(argThat(hasProperty("code", is(1013))));
    }

    @Test
    void sendPings_ShouldSendPingMessageToAllOpenSessions() throws Exception {
        ReflectionTestUtils.setField(registry, "connectionCap", 5);
        when(session1.getId()).thenReturn("sess-1");
        when(session1.isOpen()).thenReturn(true);

        registry.register(1L, session1);

        // 2. 주기적인 Heartbeat Ping 발송 검증 (D47 계약 준수)
        registry.sendPings();
        verify(session1, times(1)).sendMessage(any(PingMessage.class));
    }

    @Test
    void onApplicationEvent_ShouldDrainAllSessionsWith1001AndClearPresence() throws Exception {
        ReflectionTestUtils.setField(registry, "connectionCap", 5);
        
        // Mock Session 세부 세팅
        Map<String, Object> attrs1 = new HashMap<>();
        attrs1.put("userId", 101L);
        when(session1.getId()).thenReturn("sess-1");
        when(session1.isOpen()).thenReturn(true);
        when(session1.getAttributes()).thenReturn(attrs1);

        Map<String, Object> attrs2 = new HashMap<>();
        attrs2.put("userId", 202L);
        when(session2.getId()).thenReturn("sess-2");
        when(session2.isOpen()).thenReturn(true);
        when(session2.getAttributes()).thenReturn(attrs2);

        // 세션 등록
        registry.register(1L, session1);
        registry.register(2L, session2);

        // 3. 스프링 종료 이벤트 트리거 - Graceful Drain 검증 (D15 계약 준수)
        ContextClosedEvent closedEvent = new ContextClosedEvent(mock(ApplicationContext.class));
        registry.onApplicationEvent(closedEvent);

        // Presence 일괄 정리 leave 검증
        verify(roomPresenceService, times(1)).leave(1L, 101L);
        verify(roomPresenceService, times(1)).leave(2L, 202L);

        // GOING_AWAY (1001 코드) 세션 강제 종료 검증
        verify(session1, times(1)).close(CloseStatus.GOING_AWAY);
        verify(session2, times(1)).close(CloseStatus.GOING_AWAY);
    }
}
