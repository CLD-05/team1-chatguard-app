package com.chatguard.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record RoomResponse(
    Long id,
    String name,
    @JsonProperty("streamer_name")
    String streamerName,
    @JsonProperty("created_at")
    LocalDateTime createdAt,
    @JsonProperty("presence_count")
    int presenceCount
) {
}
