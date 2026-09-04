package com.ultimacuota.controllers;

import com.ultimacuota.dto.*;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.RaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/races")
@RequiredArgsConstructor
public class RaceController {

    private final RaceService raceService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getAll(
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("races", raceService.getAll(estado))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("race", raceService.getById(id))));
    }

    @GetMapping("/{id}/odds")
    public ResponseEntity<ApiResponse<Object>> getOdds(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(raceService.getOdds(id)));
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<ApiResponse<Object>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("results", raceService.getResults(id))));
    }

    @PostMapping("/{id}/inscribe")
    public ResponseEntity<ApiResponse<Object>> inscribe(
            @PathVariable Long id,
            @RequestBody InscribeRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(raceService.inscribe(id, request, user.getId())));
    }

    @PostMapping("/{id}/bet")
    public ResponseEntity<ApiResponse<Object>> placeBet(
            @PathVariable Long id,
            @RequestBody BetRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(raceService.placeBet(id, request, user.getId())));
    }
}
