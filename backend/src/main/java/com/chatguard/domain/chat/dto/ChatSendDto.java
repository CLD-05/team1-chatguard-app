package com.chatguard.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatSendDto(
        @JsonProperty("room_id") Long roomId,
        String content) {
}