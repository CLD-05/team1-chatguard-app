package com.chatguard.domain.chat.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatRoomSessionRegistry {

    private final ConcurrentHashMap<Long, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();

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
}
