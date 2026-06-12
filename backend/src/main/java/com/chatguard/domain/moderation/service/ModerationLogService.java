package com.chatguard.domain.moderation.service;

import com.chatguard.domain.moderation.entity.ModerationLog;
import com.chatguard.domain.moderation.repository.ModerationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModerationLogService {

    private final ModerationLogRepository moderationLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInNewTransaction(ModerationLog log) {
        moderationLogRepository.save(log);
    }
}
