package com.chatguard.domain.moderation.service;

import com.chatguard.domain.moderation.entity.BannedWord;
import com.chatguard.domain.moderation.repository.BannedWordRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    public Page<BannedWord> getBannedWords(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return bannedWordRepository.findByWordContaining(keyword.trim(), pageable);
        }
        return bannedWordRepository.findAll(pageable);
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

        // 트랜잭션 커밋 후 또는 즉시 캐시 갱신 및 무효화 이벤트 발행
        executeAfterCommitOrImmediately(() -> {
            textModerationService.refreshCache();
            publishInvalidation();
        });

        return saved;
    }

    @Transactional
    public void deleteBannedWord(Long id) {
        BannedWord bannedWord = bannedWordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 금칙어입니다."));

        bannedWordRepository.delete(bannedWord);

        // 트랜잭션 커밋 후 또는 즉시 캐시 갱신 및 무효화 이벤트 발행
        executeAfterCommitOrImmediately(() -> {
            textModerationService.refreshCache();
            publishInvalidation();
        });
    }

    private void executeAfterCommitOrImmediately(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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
