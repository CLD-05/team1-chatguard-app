package com.chatguard.global.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.chatguard.domain.room.repository.RoomRepository;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtProvider jwtProvider;
    private final RoomRepository roomRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }

        // 1) 토큰 검증 — Sec-WebSocket-Protocol 헤더에서 추출 (D21·D29)
        String token = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (token != null) {
            token = token.trim();
        }
        Claims claims = token != null ? jwtProvider.getClaimsIfValid(token) : null;
        if (claims == null) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }

        // 2) room_id 누락 → 404 (D26·D29)
        String roomIdParam = servletRequest.getServletRequest().getParameter("room_id");
        if (roomIdParam == null || roomIdParam.isBlank()) {
            return reject(response, HttpStatus.NOT_FOUND);
        }

        // 3) room_id 파싱 가드 — 비숫자 → 404 (500 금지)
        Long roomId;
        try {
            roomId = Long.parseLong(roomIdParam.trim());
        } catch (NumberFormatException e) {
            return reject(response, HttpStatus.NOT_FOUND);
        }

        // 4) 방 존재 확인 — 미존재 → 404 (D26)
        if (!roomRepository.existsById(roomId)) {
            return reject(response, HttpStatus.NOT_FOUND);
        }

        attributes.put("userId", Long.parseLong(claims.getSubject()));
        attributes.put("roomId", roomId);
        attributes.put("displayName", claims.get("display_name", String.class));
        return true;
    }

    private boolean reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
