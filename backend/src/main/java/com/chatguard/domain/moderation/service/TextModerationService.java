package com.chatguard.domain.moderation.service;

import com.chatguard.domain.moderation.entity.Verdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
public class TextModerationService {

    private static final Set<String> BANNED_KEYWORDS = Set.of(
        "badword", 
        "욕설", 
        "금칙어",
        "비속어"
    );

    public Verdict judge(String content) {
        if (content == null || content.isBlank()) {
            return Verdict.PASS;
        }

        String lowerContent = content.toLowerCase();
        
        boolean isBlocked = BANNED_KEYWORDS.stream()
                .anyMatch(lowerContent::contains);

        if (isBlocked) {
            log.info("Message blocked by keyword moderation: {}", content);
            return Verdict.BLOCK;
        }

        return Verdict.PASS;
    }
}
