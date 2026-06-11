package com.chatguard.domain.chat.ws;

import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.service.ChatService;
import com.chatguard.domain.chat.service.ChatService.SendMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

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
            if (userId == null) return;

            JsonNode payload = root.path("payload");
            ChatSendDto dto = objectMapper.treeToValue(payload, ChatSendDto.class);
            Long sessionRoomId = getRoomId(session);
            if (dto.roomId() == null) {
                sendError(session, "INVALID_PAYLOAD", "room_id is required.");
                return;
            }
            if (!dto.roomId().equals(sessionRoomId)) {
                sendError(session, "ROOM_MISMATCH", "연결된 채팅방과 메시지 채팅방이 다릅니다.");
                return;
            }

            try {
                SendMessageResult result = chatService.sendMessage(userId, dto);
                if (result == SendMessageResult.BLOCKED_KEYWORD) {
                    sendError(session, "MESSAGE_BLOCKED", "금칙어가 포함되어 있습니다.");
                }
            } catch (IllegalArgumentException e) {
                sendError(session, "INVALID_PAYLOAD", e.getMessage());
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

    private void sendError(WebSocketSession session, String code, String message) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "error",
            "payload", Map.of(
                "code", code,
                "message", message
            )
        ))));
    }
}
