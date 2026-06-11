package com.chatguard.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chatguard.domain.chat.entity.ModerationLog;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
    default ModerationLog saveInNewTransaction(ModerationLog log) {
        return save(log);
    }
}
