package com.chatguard.domain.chat.ws;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.CloseStatus;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatRoomSessionRegistry {

    private final ConcurrentHashMap<Long, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    @Value("${WS_CONNECTION_CAP:200}")
    private int connectionCap;

    public ChatRoomSessionRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // B-2: ws_active_connections (gauge) 등록
        meterRegistry.gauge("ws_active_connections", this, registry -> 
            registry.rooms.values().stream().mapToInt(Map::size).sum()
        );
    }

    public void register(Long roomId, WebSocketSession session) throws IOException {
        int total = rooms.values().stream().mapToInt(Map::size).sum();
        if (total >= connectionCap) {
            log.warn("WS connection cap reached: current={}, cap={}, session={}", total, connectionCap, session.getId());
            session.close(new CloseStatus(1008, "connection cap reached"));
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

    @PreDestroy
    public void closeAllSessions() {
        int totalSessions = rooms.values().stream().mapToInt(Map::size).sum();
        log.info("Graceful Drain: Context closing. Attempting to close {} active sessions with status 1001", totalSessions);
        
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
