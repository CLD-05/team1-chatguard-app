package com.chatguard.domain.room.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RoomCreateRequest {

    private String name;
    private String streamerName;
}
