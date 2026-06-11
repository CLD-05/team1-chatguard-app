package com.chatguard.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatSendDto(
    @JsonProperty("room_id")
    Long roomId,
    @JsonProperty("user_id")
    Long userId,
    String content
) {
}
