package com.chatguard.domain.admin.dto;

public record AdminStatsResponse(
        long totalMessages,
        long keywordBlocked,
        long aiBlurred
) {}
