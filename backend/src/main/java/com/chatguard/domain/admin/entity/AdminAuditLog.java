package com.chatguard.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "admin_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false, length = 255)
    private String adminId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @Builder
    public AdminAuditLog(String adminId, String action, String resourceId, String description) {
        this.adminId = adminId;
        this.action = action;
        this.resourceId = resourceId;
        this.description = description;
    }
}
