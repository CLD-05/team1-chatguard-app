package com.chatguard.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLoginResponse(
    UserDto user,
    String token
) {
    public record UserDto(
        Long id,
        String username,
        @JsonProperty("display_name")
        String displayName
    ) {
    }

}
