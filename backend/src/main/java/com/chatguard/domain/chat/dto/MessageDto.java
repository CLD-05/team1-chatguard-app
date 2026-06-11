package com.chatguard.domain.chat.dto;

import java.time.LocalDateTime;

import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;

@Getter
public class MessageDto {

    private final String id;
    private final Long roomId;
    private final Long userId;
    private final String displayName;
    private final String content;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private final LocalDateTime createdAt;
    private final MessageStatus status;

    private MessageDto(Message message) {
        this.id = message.getId();
        this.roomId = message.getRoom().getId();
        this.userId = message.getUser().getId();
        this.displayName = message.getUser().getDisplayName();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
        this.status = message.getStatus();
    }

    public static MessageDto from(Message message) {
        return new MessageDto(message);
    }
}
