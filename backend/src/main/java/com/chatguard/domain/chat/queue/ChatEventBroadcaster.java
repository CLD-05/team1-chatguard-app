package com.chatguard.domain.chat.queue;

import com.chatguard.domain.chat.dto.ChatEventDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatEventBroadcaster {
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public ChatEventBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Long roomId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(WebSocketSession session) {
        roomSessions.values().forEach(sessions -> sessions.remove(session));
    }

    public void broadcast(Long roomId, String type, Object payload) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String json = toJson(new ChatEventDto(type, payload));
        sessions.removeIf(session -> !session.isOpen());
        sessions.forEach(session -> send(session, json));
    }

    private String toJson(ChatEventDto event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat event", e);
        }
    }

    private void send(WebSocketSession session, String json) {
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            unregister(session);
        }
    }
}
