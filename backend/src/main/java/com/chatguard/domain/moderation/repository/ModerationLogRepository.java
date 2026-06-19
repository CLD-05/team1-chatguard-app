package com.chatguard.domain.moderation.repository;

import com.chatguard.domain.moderation.entity.ModerationLog;
import com.chatguard.domain.moderation.entity.Stage;
import com.chatguard.domain.moderation.entity.Verdict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {

    long countByStageAndVerdict(Stage stage, Verdict verdict);

    @Query("SELECT m FROM ModerationLog m WHERE (:stage IS NULL OR m.stage = :stage) AND (:verdict IS NULL OR m.verdict = :verdict) AND (:before IS NULL OR m.id < :before) ORDER BY m.id DESC")
    List<ModerationLog> findWithFilters(@Param("stage") Stage stage, @Param("verdict") Verdict verdict, @Param("before") Long before, Pageable pageable);
}
