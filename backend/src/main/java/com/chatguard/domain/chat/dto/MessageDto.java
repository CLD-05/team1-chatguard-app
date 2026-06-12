package com.chatguard.domain.chat.dto;

import java.time.LocalDateTime;

import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageDto(
        String id,
        @JsonProperty("room_id") Long roomId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("display_name") String displayName,
        String content,
        @JsonProperty("created_at") LocalDateTime createdAt,
        MessageStatus status) {
    public static MessageDto from(Message message) {
        return from(message, message.getUser().getDisplayName());
    }

    public static MessageDto from(Message message, String displayName) {
        return new MessageDto(
                message.getId(),
                message.getRoom().getId(),
                message.getUser().getId(),
                displayName,
                message.getContent(),
                message.getCreatedAt(),
                message.getStatus());
    }
}