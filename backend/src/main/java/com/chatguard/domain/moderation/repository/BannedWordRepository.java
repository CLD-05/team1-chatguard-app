package com.chatguard.domain.moderation.repository;

import com.chatguard.domain.moderation.entity.BannedWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BannedWordRepository extends JpaRepository<BannedWord, Long> {
    boolean existsByWord(String word);
    Optional<BannedWord> findByWord(String word);
    void deleteByWord(String word);
    Page<BannedWord> findByWordContaining(String word, Pageable pageable);
}
