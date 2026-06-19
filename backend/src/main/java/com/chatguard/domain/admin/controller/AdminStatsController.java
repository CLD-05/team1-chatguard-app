package com.chatguard.domain.admin.controller;

import com.chatguard.domain.admin.dto.AdminStatsResponse;
import com.chatguard.domain.admin.dto.ModerationLogResponse;
import com.chatguard.domain.admin.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    // D46: GET /api/admin/stats — 대시보드 집계(총 메시지·1차 차단·2차 블러)
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminStatsService.getStats());
    }

    // D46: GET /api/admin/moderation-logs?stage={KEYWORD|AI|all}&verdict={PASS|BLOCK}&before={id}&limit=50
    // 대시보드 최근 검열 내역 + 로그 탭 공용
    @GetMapping("/moderation-logs")
    public ResponseEntity<List<ModerationLogResponse>> getLogs(
            @RequestParam(defaultValue = "all") String stage,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(adminStatsService.getLogs(stage, verdict, before, limit));
    }
}
