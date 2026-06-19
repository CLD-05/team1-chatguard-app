package com.chatguard.domain.admin.service;

import com.chatguard.domain.admin.dto.AdminStatsResponse;
import com.chatguard.domain.admin.dto.ModerationLogResponse;
import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.moderation.entity.ModerationLog;
import com.chatguard.domain.moderation.entity.Stage;
import com.chatguard.domain.moderation.entity.Verdict;
import com.chatguard.domain.moderation.repository.ModerationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private final ModerationLogRepository moderationLogRepository;
    private final MessageRepository messageRepository;

    public AdminStatsResponse getStats() {
        long keywordBlocked = moderationLogRepository.countByStageAndVerdict(Stage.KEYWORD, Verdict.BLOCK);
        long aiBlurred = moderationLogRepository.countByStageAndVerdict(Stage.AI, Verdict.BLOCK);
        long totalMessages = moderationLogRepository.count();
        return new AdminStatsResponse(totalMessages, keywordBlocked, aiBlurred);
    }

    public List<ModerationLogResponse> getLogs(String stage, String verdict, Long before, int limit) {
        Pageable pageable = PageRequest.of(0, limit);

        Stage stageEnum = (stage == null || "all".equalsIgnoreCase(stage)) ? null : Stage.valueOf(stage.toUpperCase());
        Verdict verdictEnum = (verdict == null) ? null : Verdict.valueOf(verdict.toUpperCase());

        List<ModerationLog> logs = moderationLogRepository.findWithFilters(stageEnum, verdictEnum, before, pageable);

        // AI 행은 moderation_logs.content가 null → messages 테이블에서 조회
        List<String> messageIdsNeeded = logs.stream()
                .filter(l -> l.getContent() == null)
                .map(ModerationLog::getMessageId)
                .collect(Collectors.toList());

        Map<String, String> messageContentMap = messageIdsNeeded.isEmpty()
                ? Map.of()
                : messageRepository.findAllById(messageIdsNeeded).stream()
                        .collect(Collectors.toMap(Message::getId, Message::getContent));

        return logs.stream()
                .map(l -> {
                    String content = l.getContent() != null
                            ? l.getContent()
                            : messageContentMap.get(l.getMessageId());
                    return new ModerationLogResponse(
                            l.getId(), l.getStage().name(), l.getVerdict().name(),
                            l.getScore(), content, l.getCheckedAt());
                })
                .collect(Collectors.toList());
    }
}
