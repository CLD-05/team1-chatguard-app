package com.chatguard.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatSendDto(
    @JsonProperty("user_id")
    Long userId,
    String content
) {

}
