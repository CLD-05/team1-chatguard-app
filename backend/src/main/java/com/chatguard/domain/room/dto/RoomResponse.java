package com.chatguard.domain.room.dto;

import com.chatguard.domain.room.entity.Room;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record RoomResponse(
    Long id,
    String name,
    @JsonProperty("streamer_name")
    String streamerName,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
            room.getId(),
            room.getName(),
            room.getStreamerName(),
            room.getCreatedAt()
        );
    }
}
