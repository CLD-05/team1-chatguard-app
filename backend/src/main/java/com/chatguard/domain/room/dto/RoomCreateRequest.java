package com.chatguard.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomCreateRequest(
    String name,
    @JsonProperty("streamer_name")
    String streamerName
) {

}
