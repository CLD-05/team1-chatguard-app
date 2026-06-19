package com.chatguard.domain.moderation.service;

import com.chatguard.domain.moderation.entity.BannedWord;
import com.chatguard.domain.moderation.repository.BannedWordRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminKeywordService {

    private final BannedWordRepository bannedWordRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final TextModerationService textModerationService;

    @Value("${CONFIG_CHANNEL_PREFIX:config:}")
    private String configChannelPrefix;

    @Transactional(readOnly = true)
    public List<BannedWord> getBannedWords() {
        return bannedWordRepository.findAll();
    }

    @Transactional
    public BannedWord addBannedWord(String word, Long userId) {
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("금칙어는 비어 있을 수 없습니다.");
        }
        String cleanWord = word.trim();
        if (bannedWordRepository.findByWord(cleanWord).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 금칙어입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        BannedWord bannedWord = BannedWord.builder()
                .word(cleanWord)
                .createdBy(user)
                .build();

        BannedWord saved = bannedWordRepository.save(bannedWord);

        // 로컬 JVM 캐시 동시 갱신
        textModerationService.refreshCache();

        // Redis Pub/Sub을 통한 전역 무효화 이벤트 발행
        publishInvalidation();

        return saved;
    }

    @Transactional
    public void deleteBannedWord(Long id) {
        BannedWord bannedWord = bannedWordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 금칙어입니다."));

        bannedWordRepository.delete(bannedWord);

        // 로컬 JVM 캐시 동시 갱신
        textModerationService.refreshCache();

        // Redis Pub/Sub을 통한 전역 무효화 이벤트 발행
        publishInvalidation();
    }

    private void publishInvalidation() {
        String channel = configChannelPrefix + "banned-words";
        try {
            redisTemplate.convertAndSend(channel, "invalidate");
            log.info("Published cache invalidation message to channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to publish cache invalidation message to channel: {}", channel, e);
        }
    }
}
