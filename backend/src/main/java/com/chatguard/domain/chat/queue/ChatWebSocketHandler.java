package com.chatguard.domain.chat.queue;

import com.chatguard.domain.chat.service.ChatService;
import com.chatguard.global.auth.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ChatService chatService;
    private final ChatEventBroadcaster broadcaster;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(
        ChatService chatService,
        ChatEventBroadcaster broadcaster,
        JwtProvider jwtProvider,
        ObjectMapper objectMapper
    ) {
        this.chatService = chatService;
        this.broadcaster = broadcaster;
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long roomId = getLongQueryParam(session.getUri(), "room_id");
        if (roomId == null) {
            closeQuietly(session, CloseStatus.BAD_DATA);
            return;
        }
        session.getAttributes().put("roomId", roomId);
        broadcaster.register(roomId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        if (!"chat.send".equals(root.path("type").asText())) {
            return;
        }

        Long roomId = (Long) session.getAttributes().get("roomId");
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            userId = jwtProvider.getUserId(getStringQueryParam(session.getUri(), "token"));
        }
        String content = root.path("payload").path("content").asText(null);

        chatService.sendMessage(roomId, userId, content);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
    }

    private Long getLongQueryParam(URI uri, String name) {
        String value = getStringQueryParam(uri, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }

    private String getStringQueryParam(URI uri, String name) {
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
        }
    }
}
