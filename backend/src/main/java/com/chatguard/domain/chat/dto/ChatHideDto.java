package com.chatguard.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatHideDto(
    String id,
    String action,
    @JsonProperty("room_id")
    Long roomId
) {

}
