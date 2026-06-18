package com.chatguard.domain.moderation.repository;

import com.chatguard.domain.moderation.entity.BannedWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BannedWordRepository extends JpaRepository<BannedWord, Long> {
    boolean existsByWord(String word);
    Optional<BannedWord> findByWord(String word);
    void deleteByWord(String word);
}
