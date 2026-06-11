package com.chatguard.domain.chat.dto;

import com.chatguard.domain.chat.entity.Message;

public record ChatMessageDto(
    String type,
    MessageDto payload
) {
    public static ChatMessageDto from(Message message) {
        return new ChatMessageDto("chat.message", MessageDto.from(message));
    }
}
