package com.chatguard.domain.chat.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class ChatRoomSessionRegistry {

    // roomId → 접속 중인 WebSocketSession 목록
    private final Map<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public void register(Long roomId, WebSocketSession session) {
        rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(Long roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = rooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) rooms.remove(roomId);
        }
    }

    public void broadcast(Long roomId, String payload) {
        Set<WebSocketSession> sessions = rooms.getOrDefault(roomId, Set.of());
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Failed to send message to session {}", session.getId(), e);
                }
            }
        }
    }
}
