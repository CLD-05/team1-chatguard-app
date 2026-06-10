package com.chatguard.domain.chat.dto;

import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MessageDto {

    private final String id;
    private final Long roomId;
    private final Long userId;
    private final String displayName;
    private final String content;
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
