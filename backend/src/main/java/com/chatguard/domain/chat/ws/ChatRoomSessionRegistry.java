package com.chatguard.domain.chat.ws;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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

    public ChatRoomSessionRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // B-2: ws_active_connections (gauge) 등록
        meterRegistry.gauge("ws_active_connections", this, registry -> 
            registry.rooms.values().stream().mapToInt(Map::size).sum()
        );
    }

    public void register(Long roomId, WebSocketSession session) {
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

    @PreDestroy
    public void closeAllSessions() {
        int totalSessions = rooms.values().stream().mapToInt(Map::size).sum();
        log.info("Graceful Drain: Context closing. Attempting to close {} active sessions with status 1001", totalSessions);
        
        rooms.values().forEach(sessionMap -> 
            sessionMap.values().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        session.close(CloseStatus.SERVICE_RESTARTED);
                    } catch (IOException e) {
                        log.warn("Failed to close session during drain: sessionID={}", session.getId());
                    }
                }
            })
        );
        log.info("Graceful Drain: Finished closing all sessions.");
    }
}
