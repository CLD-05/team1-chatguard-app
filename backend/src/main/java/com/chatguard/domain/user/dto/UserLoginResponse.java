package com.chatguard.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLoginResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("access_token") String accessToken
) {}
