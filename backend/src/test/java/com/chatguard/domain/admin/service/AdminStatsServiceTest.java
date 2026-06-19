package com.chatguard.domain.admin.service;

import com.chatguard.domain.admin.dto.AdminStatsResponse;
import com.chatguard.domain.admin.dto.ModerationLogResponse;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.moderation.entity.ModerationLog;
import com.chatguard.domain.moderation.entity.Stage;
import com.chatguard.domain.moderation.entity.Verdict;
import com.chatguard.domain.moderation.repository.ModerationLogRepository;
import com.chatguard.global.error.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminStatsServiceTest {

    private ModerationLogRepository moderationLogRepository;
    private MessageRepository messageRepository;
    private AdminStatsService adminStatsService;

    @BeforeEach
    void setUp() {
        moderationLogRepository = mock(ModerationLogRepository.class);
        messageRepository = mock(MessageRepository.class);
        adminStatsService = new AdminStatsService(moderationLogRepository, messageRepository);
    }

    @Test
    void getStats_총메시지는_messages수와_키워드차단수의_합이다() {
        when(messageRepository.count()).thenReturn(10L);
        when(moderationLogRepository.countByStageAndVerdict(Stage.KEYWORD, Verdict.BLOCK)).thenReturn(3L);
        when(moderationLogRepository.countByStageAndVerdict(Stage.AI, Verdict.BLOCK)).thenReturn(2L);

        AdminStatsResponse result = adminStatsService.getStats();

        assertThat(result.totalMessages()).isEqualTo(13L);
        assertThat(result.keywordBlocked()).isEqualTo(3L);
        assertThat(result.aiBlurred()).isEqualTo(2L);
    }

    @Test
    void getLogs_잘못된_stage_파라미터는_400을_반환한다() {
        assertThatThrownBy(() -> adminStatsService.getLogs("INVALID", null, null, 50))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void getLogs_잘못된_verdict_파라미터는_400을_반환한다() {
        when(moderationLogRepository.findWithFilters(any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> adminStatsService.getLogs("all", "INVALID", null, 50))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void getLogs_정상_조회시_결과를_반환한다() {
        ModerationLog log = mock(ModerationLog.class);
        when(log.getId()).thenReturn(1L);
        when(log.getStage()).thenReturn(Stage.KEYWORD);
        when(log.getVerdict()).thenReturn(Verdict.BLOCK);
        when(log.getScore()).thenReturn(null);
        when(log.getContent()).thenReturn("badword");
        when(log.getMessageId()).thenReturn("01ABC");
        when(log.getCheckedAt()).thenReturn(LocalDateTime.now());
        when(moderationLogRepository.findWithFilters(any(), any(), any(), any())).thenReturn(List.of(log));

        List<ModerationLogResponse> result = adminStatsService.getLogs("all", null, null, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("badword");
    }
}
