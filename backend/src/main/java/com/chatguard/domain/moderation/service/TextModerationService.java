package com.chatguard.domain.moderation.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chatguard.domain.moderation.entity.Verdict;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TextModerationService {
	
	@PostConstruct
	public void init() {
	    // 서버가 켜질 때 주입된 리스트의 진짜 내용물과 크기(Size)를 터미널에 출력합니다.
	    log.info("🔥 주입된 금칙어 리스트 진짜 내용물: {}", bannedKeywords);
	    log.info("🔥 주입된 금칙어 개수: {}", bannedKeywords != null ? bannedKeywords.size() : 0);
	}

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
                        // 🌟 [핵심] 만약 윈도우 환경 때문에 글자가 깨져서 들어왔다면 (ISO-8859-1 규격)
                        // 자바 바이트 레벨에서 깨진 글자를 원본 UTF-8 청정 한글로 강제 변환(복원)합니다.
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
