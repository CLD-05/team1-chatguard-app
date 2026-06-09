package com.chatguard.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModerationResultRequest(
    @JsonProperty("message_id")
    String messageId,
    String verdict,
    Float score,
    String action,
    @JsonProperty("model_version")
    String modelVersion,
    String reason
) {
}
