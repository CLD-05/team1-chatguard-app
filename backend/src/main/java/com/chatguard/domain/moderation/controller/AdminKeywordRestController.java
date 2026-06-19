package com.chatguard.domain.moderation.controller;

import com.chatguard.domain.moderation.entity.BannedWord;
import com.chatguard.domain.moderation.service.AdminKeywordService;
import com.chatguard.global.auth.LoginUser;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/keywords")
@RequiredArgsConstructor
public class AdminKeywordRestController {

    private final AdminKeywordService adminKeywordService;

    @GetMapping
    public ResponseEntity<List<BannedWordResponse>> getKeywords() {
        List<BannedWordResponse> responses = adminKeywordService.getBannedWords().stream()
                .map(BannedWordResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    public ResponseEntity<BannedWordResponse> addKeyword(
            @LoginUser Long userId,
            @RequestBody BannedWordRequest request
    ) {
        BannedWord saved = adminKeywordService.addBannedWord(request.getWord(), userId);
        return ResponseEntity.ok(BannedWordResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKeyword(@PathVariable Long id) {
        adminKeywordService.deleteBannedWord(id);
        return ResponseEntity.ok().build();
    }

    @Getter
    @NoArgsConstructor
    public static class BannedWordRequest {
        private String word;

        public BannedWordRequest(String word) {
            this.word = word;
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class BannedWordResponse {
        private final Long id;
        private final String word;

        public static BannedWordResponse from(BannedWord bannedWord) {
            return new BannedWordResponse(bannedWord.getId(), bannedWord.getWord());
        }
    }
}
