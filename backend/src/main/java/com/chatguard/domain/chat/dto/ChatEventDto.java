package com.chatguard.domain.chat.dto;

public record ChatEventDto(
    String type,
    Object payload
) {
}
