package com.chatguard.domain.chat.ws;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.chatguard.domain.admin.service.RoomFreezeService;
import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.service.ChatService;
import com.chatguard.domain.chat.service.RoomPresenceService;
import com.chatguard.domain.chat.service.ChatService.SendMessageResult;
import com.chatguard.global.error.CustomException;
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
    private final RoomFreezeService roomFreezeService;
    private final RoomPresenceService roomPresenceService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long roomId = getRoomId(session);
        if (roomId != null) {
            Long userId = getUserId(session);
            String displayName = getDisplayName(session);

            // A-3: register 전에 join — publish 시점에 신규 세션이 registry에 없으므로 포워딩 안 됨
            if (userId != null && displayName != null) {
                roomPresenceService.join(roomId, userId, displayName);
            }
            registry.register(roomId, session);
            log.info("WS connected: session={} roomId={} userId={}", session.getId(), roomId, userId);

            // 신규 접속자에게 현재 freeze 상태 1회 전송 (D45)
            boolean frozen = roomFreezeService.isFrozen(roomId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "room.freeze",
                "payload", Map.of("room_id", roomId, "frozen", frozen)
            ))));

            // A-3: presence 스냅샷 직접 전송 — 정확히 1회
            if (userId != null && displayName != null) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "presence.update",
                    "payload", roomPresenceService.getSnapshot(roomId)
                ))));
            }
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
            String displayName = getDisplayName(session);

            JsonNode payload = root.path("payload");
            ChatSendDto dto = objectMapper.treeToValue(payload, ChatSendDto.class);
            if (dto.roomId() == null) {
                sendError(session, "INVALID_PAYLOAD", "room_id is required.");
                return;
            }
            if (!dto.roomId().equals(sessionRoomId)) {
                sendError(session, "ROOM_MISMATCH", "연결된 채팅방과 메시지 채팅방이 다릅니다.");
                return;
            }

            String content = dto.content();
            if (content == null || content.trim().isEmpty()) {
                sendError(session, "INVALID_PAYLOAD", "content is required");
                return;
            }
            String normalizedContent = content.trim();
            if (normalizedContent.length() > 500) {
                sendError(session, "INVALID_PAYLOAD", "content must be 500 characters or less");
                return;
            }

            try {
                SendMessageResult result = chatService.sendMessage(userId, displayName, dto);
                if (result == SendMessageResult.BLOCKED_KEYWORD) {
                    sendError(session, "MESSAGE_BLOCKED", "금칙어가 포함되어 있습니다.");
                } else if (result == SendMessageResult.BLOCKED_FROZEN) {
                    sendError(session, "CHAT_FROZEN", "채팅이 일시중지 상태입니다.");
                }
            } catch (IllegalArgumentException e) {
                sendError(session, "INVALID_PAYLOAD", e.getMessage());
            } catch (CustomException e) {
                log.warn("Chat message rejected by service: code={}", e.getErrorCode().name());
                sendError(session, "INTERNAL", e.getMessage());
            } catch (IllegalStateException e) {
                log.error("Failed to enqueue chat message for moderation", e);
                sendError(session, "INTERNAL", "메시지 전송에 실패했습니다. 다시 시도해주세요.");
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = getRoomId(session);
        Long userId = getUserId(session);
        if (roomId != null) {
            registry.unregister(roomId, session);
            // D44: presence leave — join()과 동일 조건으로 가드 (비대칭 삭제 방지)
            String displayName = getDisplayName(session);
            if (userId != null && displayName != null) {
                roomPresenceService.leave(roomId, userId);
            }
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

    private String getDisplayName(WebSocketSession session) {
        return (String) session.getAttributes().get("displayName");
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
