package com.chatguard.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record ModerationLogResponse(
        Long id,
        String stage,
        String verdict,
        Float score,
        String content,
        @JsonProperty("checked_at") OffsetDateTime checkedAt
) {}
