package com.chatguard.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLoginResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("display_name") String displayName,
        // A-2 동결 스펙: 로그인 응답 필드명은 token. (이전 access_token에서 정렬)
        @JsonProperty("token") String token
) {}
