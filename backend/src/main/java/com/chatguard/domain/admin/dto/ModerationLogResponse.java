package com.chatguard.domain.admin.dto;

import java.time.LocalDateTime;

public record ModerationLogResponse(
        Long id,
        String stage,
        String verdict,
        Float score,
        String content,
        LocalDateTime checkedAt
) {}
