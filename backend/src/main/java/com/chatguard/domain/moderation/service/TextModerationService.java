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

    private static final java.util.regex.Pattern HANGUL_PATTERN = java.util.regex.Pattern.compile("[^가-힣ㄱ-ㅎㅏ-ㅣ]");
    
    private static final String CHOSUNG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String JUNGSUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String JONGSUNG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"; // 첫 문자 공백(종성 없음 표현)

    // 캐시 레퍼런스 감지를 통한 셋 분할 최적화
    private volatile Set<String> lastBannedKeywordsRef = null;
    private volatile Set<String> cachedKoreanBannedKeywords = Set.of();
    private volatile Set<String> cachedNonKoreanBannedKeywords = Set.of();

    private boolean isKoreanOnly(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            // 완성형 한글(0xAC00~0xD7A3) 및 한글 자모(0x3130~0x318F) 범위 체크
            boolean isHangul = (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x3130 && c <= 0x318F);
            if (!isHangul) {
                return false;
            }
        }
        return true;
    }

    private static String combineJamo(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int len = text.length();
        while (i < len) {
            char c = text.charAt(i);
            int choIdx = CHOSUNG.indexOf(c);
            if (choIdx != -1 && i + 1 < len) {
                char next = text.charAt(i + 1);
                int jungIdx = JUNGSUNG.indexOf(next);
                if (jungIdx != -1) {
                    // 초성 + 중성이 성립함
                    int jongIdx = 0;
                    int skip = 2;
                    if (i + 2 < len) {
                        char next2 = text.charAt(i + 2);
                        int tempJong = JONGSUNG.indexOf(next2);
                        if (tempJong > 0) {
                            boolean isNextNextJungsung = false;
                            if (i + 3 < len) {
                                isNextNextJungsung = JUNGSUNG.indexOf(text.charAt(i + 3)) != -1;
                            }
                            if (!isNextNextJungsung) {
                                jongIdx = tempJong;
                                skip = 3;
                            }
                        }
                    }
                    char combined = (char) ((choIdx * 21 + jungIdx) * 28 + jongIdx + 0xAC00);
                    sb.append(combined);
                    i += skip;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private synchronized void updateCachedSplits(Set<String> currentKeywords) {
        if (lastBannedKeywordsRef == currentKeywords) {
            return;
        }
        Set<String> koSet = new java.util.HashSet<>();
        Set<String> nonKoSet = new java.util.HashSet<>();
        for (String word : currentKeywords) {
            if (word != null && !word.isBlank()) {
                if (isKoreanOnly(word)) {
                    koSet.add(word);
                } else {
                    nonKoSet.add(word);
                }
            }
        }
        this.cachedKoreanBannedKeywords = koSet;
        this.cachedNonKoreanBannedKeywords = nonKoSet;
        this.lastBannedKeywordsRef = currentKeywords;
    }

    public boolean judge(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        Set<String> currentKeywords = this.bannedKeywords;
        if (lastBannedKeywordsRef != currentKeywords) {
            updateCachedSplits(currentKeywords);
        }

        String lowerContent = content.toLowerCase();
        
        // 1. 영어, 숫자, 특수문자가 섞인 금칙어는 원본 소문자 텍스트 기준으로 1차 비교 (영문 셋만 순회)
        boolean originalMatch = cachedNonKoreanBannedKeywords.stream()
                .anyMatch(lowerContent::contains);

        if (originalMatch) {
            return true;
        }

        // 2. 한글(자모음 포함)을 제외한 모든 문자(특수문자, 숫자, 공백 등) 제거 전처리
        String cleanContent = HANGUL_PATTERN.matcher(content).replaceAll("");
        
        // 자모음 결합(Normalization) 추가
        cleanContent = java.text.Normalizer.normalize(cleanContent, java.text.Normalizer.Form.NFC);
        
        // 호환 자모 결합 오토마타 보완
        cleanContent = combineJamo(cleanContent);

        // 3. 전처리된 결과물이 비어있지 않은지 검증 후, 순수 한글 금칙어 비교 수행 (한글 셋만 순회)
        if (!cleanContent.isEmpty()) {
            return cachedKoreanBannedKeywords.stream()
                    .anyMatch(cleanContent::contains);
        }

        return false;
    }
}
