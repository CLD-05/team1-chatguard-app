package com.chatguard.domain.moderation.repository;

import com.chatguard.domain.moderation.entity.ModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {
}
