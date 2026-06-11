package com.chatguard.domain.chat.controller;

import com.chatguard.domain.chat.dto.MessageDto;
import com.chatguard.domain.chat.dto.ModerationResultRequest;
import com.chatguard.domain.chat.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/moderation")
public class ModerationRestController {
    private final ChatService chatService;

    public ModerationRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/results")
    public MessageDto applyResult(@RequestBody ModerationResultRequest request) {
        return chatService.applyModerationResult(request);
    }
}
