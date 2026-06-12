package com.chatguard.domain.chat.dto;

import java.time.LocalDateTime;

import com.chatguard.domain.chat.entity.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatMessageDto(
        String type,
        Payload payload) {
    // A-3: WS chat.message payload는 status를 포함하지 않는다(status는 REST 히스토리 전용).
    // REST는 status가 있는 MessageDto를 계속 사용한다.
    public record Payload(
            String id,
            @JsonProperty("room_id") Long roomId,
            @JsonProperty("user_id") Long userId,
            @JsonProperty("display_name") String displayName,
            String content,
            @JsonProperty("created_at") LocalDateTime createdAt) {
        static Payload from(Message message, String displayName) {
            return new Payload(
                    message.getId(),
                    message.getRoom().getId(),
                    message.getUser().getId(),
                    displayName,
                    message.getContent(),
                    message.getCreatedAt());
        }
    }

    public static ChatMessageDto from(Message message) {
        return from(message, message.getUser().getDisplayName());
    }

    public static ChatMessageDto from(Message message, String displayName) {
        return new ChatMessageDto("chat.message", Payload.from(message, displayName));
    }
}