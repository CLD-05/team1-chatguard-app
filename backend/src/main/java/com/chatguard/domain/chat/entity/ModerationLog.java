package com.chatguard.domain.chat.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "moderation_logs", indexes = {
    @Index(name = "idx_message_id", columnList = "message_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", length = 26, nullable = false)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Verdict verdict;

    private Float score;

    @Column(length = 50)
    private String modelVersion;

    @Column(length = 200)
    private String reason;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime checkedAt;

    @Builder
    public ModerationLog(
        String messageId,
        Stage stage,
        Verdict verdict,
        Float score,
        String modelVersion,
        String reason,
        String content,
        LocalDateTime checkedAt
    ) {
        this.messageId = messageId;
        this.stage = stage;
        this.verdict = verdict;
        this.score = score;
        this.modelVersion = modelVersion;
        this.reason = reason;
        this.content = content;
        this.checkedAt = checkedAt;
    }
}
