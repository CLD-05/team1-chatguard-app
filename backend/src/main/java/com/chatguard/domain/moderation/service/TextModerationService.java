package com.chatguard.domain.moderation.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chatguard.domain.moderation.entity.Verdict;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TextModerationService {

    @Value("${moderation.banned-keywords:badword}")
    private List<String> bannedKeywords;

    public Verdict judge(String content) {
        if (content == null || content.isBlank()) {
            return Verdict.PASS;
        }

        if (bannedKeywords == null || bannedKeywords.isEmpty()) {
            return Verdict.PASS;
        }

        String lowerContent = content.toLowerCase();
        
        boolean isBlocked = bannedKeywords.stream()
                .filter(word -> !word.isBlank())
                .map(word -> {
                    try {
                        if (word.matches(".*[ãìêê].*") || !java.nio.charset.Charset.forName("US-ASCII").newEncoder().canEncode(word)) {
                            return new String(word.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        }
                        return word;
                    } catch (Exception e) {
                        return word;
                    }
                })
                .anyMatch(lowerContent::contains);

        if (isBlocked) {
            log.info("Message blocked by keyword moderation (External Config): {}", content);
            return Verdict.BLOCK;
        }

        return Verdict.PASS;
    }
}
