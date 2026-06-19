package com.chatguard.domain.admin.controller;

import com.chatguard.domain.admin.dto.AdminStatsResponse;
import com.chatguard.domain.admin.dto.FreezeRequest;
import com.chatguard.domain.admin.dto.ModerationLogResponse;
import com.chatguard.domain.admin.service.AdminStatsService;
import com.chatguard.domain.admin.service.RoomFreezeService;
import com.chatguard.global.error.CustomException;
import com.chatguard.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;
    private final RoomFreezeService roomFreezeService;

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

    @PostMapping("/rooms/{id}/freeze")
    public ResponseEntity<Void> freeze(
            @PathVariable Long id,
            @RequestBody FreezeRequest request
    ) {
        if (request.frozen() == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        roomFreezeService.setFrozen(id, request.frozen());
        return ResponseEntity.ok().build();
    }
}
