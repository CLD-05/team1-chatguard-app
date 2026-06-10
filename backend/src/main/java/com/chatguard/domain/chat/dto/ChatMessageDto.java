package com.chatguard.domain.chat.dto;

import com.chatguard.domain.chat.entity.Message;
import lombok.Getter;

// WS 브로드캐스트용: { type: "chat.message", payload: { ...MessageDto } }
@Getter
public class ChatMessageDto {

    private final String type = "chat.message";
    private final MessageDto payload;

    private ChatMessageDto(Message message) {
        this.payload = MessageDto.from(message);
    }

    public static ChatMessageDto from(Message message) {
        return new ChatMessageDto(message);
    }
}
