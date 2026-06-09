package com.chatguard.domain.chat.repository;

import com.chatguard.domain.chat.entity.ModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {

}
