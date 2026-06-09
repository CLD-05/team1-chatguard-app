package com.chatguard.domain.chat.dto;

import com.chatguard.domain.chat.entity.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ChatMessageDto(
    String id,
    @JsonProperty("room_id")
    Long roomId,
    @JsonProperty("user_id")
    Long userId,
    @JsonProperty("display_name")
    String displayName,
    String content,
    String status,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
    public static ChatMessageDto from(Message message) {
        return new ChatMessageDto(
            message.getId(),
            message.getRoom().getId(),
            message.getUser().getId(),
            message.getUser().getDisplayName(),
            message.getContent(),
            message.getStatus().name(),
            message.getCreatedAt()
        );
    }

}
