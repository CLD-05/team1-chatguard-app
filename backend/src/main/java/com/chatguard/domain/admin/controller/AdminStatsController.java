package com.chatguard.domain.admin.controller;

import com.chatguard.domain.admin.dto.AdminStatsResponse;
import com.chatguard.domain.admin.dto.ModerationLogResponse;
import com.chatguard.domain.admin.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminStatsService.getStats());
    }

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
