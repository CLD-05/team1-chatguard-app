package com.chatguard.domain.moderation.controller;

import com.chatguard.domain.moderation.entity.BannedWord;
import com.chatguard.domain.moderation.service.AdminKeywordService;
import com.chatguard.global.auth.LoginUser;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/keywords")
@RequiredArgsConstructor
public class AdminKeywordRestController {

    private final AdminKeywordService adminKeywordService;

    @GetMapping
    public ResponseEntity<BannedWordPageResponse> getKeywords(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BannedWord> bannedWordPage = adminKeywordService.getBannedWords(keyword, pageable);
        List<BannedWordResponse> responses = bannedWordPage.getContent().stream()
                .map(BannedWordResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new BannedWordPageResponse(
                responses,
                bannedWordPage.getTotalPages(),
                bannedWordPage.getTotalElements(),
                bannedWordPage.getNumber()
        ));
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
        private final LocalDateTime createdAt;

        public static BannedWordResponse from(BannedWord bannedWord) {
            return new BannedWordResponse(bannedWord.getId(), bannedWord.getWord(), bannedWord.getCreatedAt());
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class BannedWordPageResponse {
        private final List<BannedWordResponse> content;
        private final int totalPages;
        private final long totalElements;
        private final int currentPage;
    }
}
