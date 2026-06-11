package com.chatguard.domain.room.dto;

import com.chatguard.domain.room.entity.Room;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RoomResponse {

    private final Long id;
    private final String name;
    private final String streamerName;
    private final LocalDateTime createdAt;

    private RoomResponse(Room room) {
        this.id = room.getId();
        this.name = room.getName();
        this.streamerName = room.getStreamerName();
        this.createdAt = room.getCreatedAt();
    }

    public static RoomResponse from(Room room) {
        return new RoomResponse(room);
    }
}
