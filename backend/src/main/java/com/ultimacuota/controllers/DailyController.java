package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.DailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily")
@RequiredArgsConstructor
public class DailyController {

    private final DailyService dailyService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Object>> getStatus(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(dailyService.getStatus(user.getId())));
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<Object>> claimDaily(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(dailyService.claimDaily(user.getId())));
    }
}
