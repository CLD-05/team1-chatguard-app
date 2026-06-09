package com.chatguard.domain.chat.controller;

import com.chatguard.domain.chat.dto.ChatMessageDto;
import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
public class ChatRestController {
    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatMessageDto> getMessages(
        @PathVariable Long roomId,
        @RequestParam(required = false) String before,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return chatService.getMessages(roomId, before, limit);
    }

    @PostMapping
    public ChatMessageDto sendMessage(
        @PathVariable Long roomId,
        @RequestBody ChatSendDto request
    ) {
        return chatService.sendMessage(roomId, request.userId(), request.content());
    }
}
