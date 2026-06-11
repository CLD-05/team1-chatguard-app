package com.chatguard.domain.chat.ws;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final ChatRoomSessionRegistry registry;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long roomId = getRoomId(session);
        if (roomId != null) {
            registry.register(roomId, session);
            log.info("WS connected: session={} roomId={} userId={}", session.getId(), roomId, getUserId(session));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();

        if ("chat.send".equals(type)) {
            Long userId = getUserId(session);
            Long sessionRoomId = getRoomId(session);
            if (userId == null) return;

            JsonNode payload = root.path("payload");
            ChatSendDto dto = objectMapper.treeToValue(payload, ChatSendDto.class);
            
            if (!sessionRoomId.equals(dto.getRoomId())) {
                Map<String, Object> errorEnvelope = Map.of(
                    "type", "error",
                    "payload", Map.of("code", "ROOM_MISMATCH", "message", "방 정보가 일치하지 않습니다.")
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorEnvelope)));
                return;
            }

            try {
                chatService.sendMessage(userId, dto);
            } catch (com.chatguard.global.error.CustomException e) {
                Map<String, Object> errorEnvelope = Map.of(
                    "type", "error",
                    "payload", Map.of("code", e.getErrorCode().name(), "message", e.getMessage())
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorEnvelope)));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = getRoomId(session);
        if (roomId != null) {
            registry.unregister(roomId, session);
        }
        log.info("WS disconnected: session={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS transport error: session={}", session.getId(), exception);
    }

    private Long getUserId(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }

    private Long getRoomId(WebSocketSession session) {
        return (Long) session.getAttributes().get("roomId");
    }
}
