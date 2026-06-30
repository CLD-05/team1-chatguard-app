package com.chatguard.domain.chat.ws;

import com.chatguard.domain.chat.service.RoomPresenceService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ChatRoomSessionRegistry implements ApplicationListener<ContextClosedEvent> {

    private final ConcurrentHashMap<Long, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final RoomPresenceService roomPresenceService;
    private final AtomicBoolean drained = new AtomicBoolean(false);

    @Value("${WS_CONNECTION_CAP:200}")
    private int connectionCap;

    public ChatRoomSessionRegistry(MeterRegistry meterRegistry, RoomPresenceService roomPresenceService) {
        this.meterRegistry = meterRegistry;
        this.roomPresenceService = roomPresenceService;
        // B-2: ws_active_connections (gauge) 등록
        meterRegistry.gauge("ws_active_connections", this, registry -> 
            registry.rooms.values().stream().mapToInt(Map::size).sum()
        );
    }

    public void register(Long roomId, WebSocketSession session) throws IOException {
        int total = rooms.values().stream().mapToInt(Map::size).sum();
        if (total >= connectionCap) {
            log.warn("WS connection cap reached: current={}, cap={}, session={}", total, connectionCap, session.getId());
            session.close(new CloseStatus(1013, "connection cap reached"));
            return;
        }
        WebSocketSession decoratedSession = new ConcurrentWebSocketSessionDecorator(session, 10000, 65536);
        rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), decoratedSession);
    }

    public void unregister(Long roomId, WebSocketSession session) {
        Map<String, WebSocketSession> sessions = rooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) rooms.remove(roomId);
        }
    }

    public void broadcast(Long roomId, String payload) {
        Map<String, WebSocketSession> sessions = rooms.getOrDefault(roomId, Collections.emptyMap());
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    log.warn("Failed to send message to session={}", session.getId(), e);
                }
            }
        }
    }

    /**
     * D47: 서버측 ping heartbeat. 30초마다 열린 모든 WS 세션에 ping 프레임을 보내
     * ALB·NAT 등 중간 장비의 idle 타임아웃으로 끊기는 것을 막고 좀비 세션을 감지한다.
     * 브라우저가 자동으로 pong하므로 클라 변경은 없다. 간격(30s) < ALB idle_timeout(3600s).
     */
    @Scheduled(fixedRate = 30000)
    public void sendPings() {
        int sent = 0;
        for (Map<String, WebSocketSession> sessions : rooms.values()) {
            for (WebSocketSession session : sessions.values()) {
                if (session.isOpen()) {
                    try {
                        // 세션은 ConcurrentWebSocketSessionDecorator라 sendMessage가 스레드 안전.
                        session.sendMessage(new PingMessage());
                        sent++;
                    } catch (Exception e) {
                        // 한 세션 실패가 나머지 ping을 막지 않게 — 로그만 남기고 계속.
                        log.warn("Failed to send ping to session={}", session.getId(), e);
                    }
                }
            }
        }
        if (sent > 0) {
            log.debug("WS heartbeat: sent ping to {} sessions", sent);
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (drained.compareAndSet(false, true)) {
            closeAllSessions();
        }
    }

    public void closeAllSessions() {
        int totalSessions = rooms.values().stream().mapToInt(Map::size).sum();
        log.info("Graceful Drain: Context closed event received. Attempting to clear presence and close {} active sessions with status 1001", totalSessions);
        
        // 1단계: 동기식 Presence 일괄 청소
        rooms.forEach((roomId, sessionMap) -> {
            sessionMap.values().forEach(session -> {
                Long userId = (Long) session.getAttributes().get("userId");
                if (userId != null) {
                    try {
                        roomPresenceService.leave(roomId, userId);
                    } catch (Exception e) {
                        log.warn("Failed to clear presence for roomId={}, userId={} during shutdown", roomId, userId, e);
                    }
                }
            });
        });

        // 2단계: 안전한 세션 연결 종료
        rooms.values().forEach(sessionMap -> 
            sessionMap.values().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        // D15: 서버 종료 시 1001 close code로 종료하여 클라이언트 재연결 유도
                        session.close(CloseStatus.GOING_AWAY);
                    } catch (IOException e) {
                        log.warn("Failed to close session during drain: sessionID={}", session.getId());
                    }
                }
            })
        );
        log.info("Graceful Drain: Finished closing all sessions.");
    }
}
