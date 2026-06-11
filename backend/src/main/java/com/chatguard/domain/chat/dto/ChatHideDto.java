package com.chatguard.domain.chat.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ChatHideDto {

    private final String type = "moderation.hide";
    private final Payload payload;

    public static ChatHideDto of(String messageId, String action) {
        return new ChatHideDto(new Payload(messageId, action));
    }

    @Getter
    @RequiredArgsConstructor
    public static class Payload {
        private final String id;
        private final String action;
    }
}
