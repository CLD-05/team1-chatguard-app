package com.chatguard.domain.admin.service;

import com.chatguard.domain.admin.entity.AdminAuditLog;
import com.chatguard.domain.admin.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    @Async
    @Transactional
    public void saveLog(String adminId, String action, String resourceId, String description) {
        try {
            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .adminId(adminId)
                    .action(action)
                    .resourceId(resourceId)
                    .description(description)
                    .build();

            adminAuditLogRepository.save(auditLog);
            log.debug("Successfully saved admin audit log asynchronously: action={}, adminId={}", action, adminId);
        } catch (Exception e) {
            log.error("Failed to save admin audit log asynchronously", e);
        }
    }
}
