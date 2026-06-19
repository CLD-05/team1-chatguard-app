package com.chatguard.domain.moderation.service;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.chatguard.domain.moderation.entity.BannedWord;
import com.chatguard.domain.moderation.repository.BannedWordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextModerationService {

    private final BannedWordRepository bannedWordRepository;
    private volatile Set<String> bannedKeywords = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void refreshCache() {
        log.info("Refreshing banned words cache from DB...");
        try {
            List<BannedWord> words = bannedWordRepository.findAll();
            Set<String> nextKeywords = ConcurrentHashMap.newKeySet();
            for (BannedWord bw : words) {
                if (bw.getWord() != null && !bw.getWord().isBlank()) {
                    nextKeywords.add(bw.getWord().toLowerCase().trim());
                }
            }
            this.bannedKeywords = nextKeywords;
            log.info("Banned words cache refreshed. Total unique words loaded: {}", bannedKeywords.size());
        } catch (Exception e) {
            log.error("Failed to refresh banned words cache", e);
        }
    }

    public boolean judge(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        String lowerContent = content.toLowerCase();
        
        return bannedKeywords.stream()
                .filter(word -> !word.isBlank())
                .anyMatch(lowerContent::contains);
    }
}
