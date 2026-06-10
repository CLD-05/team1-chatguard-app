package com.chatguard.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatSendDto {

    private Long roomId;
    private String content;
}
