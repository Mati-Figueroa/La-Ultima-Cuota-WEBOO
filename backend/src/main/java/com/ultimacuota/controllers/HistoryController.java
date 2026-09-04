package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping("/bets")
    public ResponseEntity<ApiResponse<Object>> getMyBets(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(historyService.getMyBets(user.getId(), estado, page, limit)));
    }

    @GetMapping("/wins")
    public ResponseEntity<ApiResponse<Object>> getMyWins(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("wins", historyService.getMyWins(user.getId()))));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(historyService.getStats(user.getId())));
    }
}
