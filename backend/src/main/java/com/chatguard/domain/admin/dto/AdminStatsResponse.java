package com.chatguard.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminStatsResponse(
        @JsonProperty("total_messages") long totalMessages,
        @JsonProperty("keyword_blocked") long keywordBlocked,
        @JsonProperty("ai_blurred") long aiBlurred
) {}
