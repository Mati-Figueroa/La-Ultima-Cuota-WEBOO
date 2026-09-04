package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.dto.RenameRequest;
import com.ultimacuota.dto.SellRequest;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.StableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stable")
@RequiredArgsConstructor
public class StableController {

    private final StableService stableService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getMyHorses(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("horses", stableService.getMyHorses(user.getId()))));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<Object>> getHorseHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("history", stableService.getHorseHistory(id, user.getId()))));
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<ApiResponse<Object>> renameHorse(
            @PathVariable Long id,
            @RequestBody RenameRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(stableService.renameHorse(id, request.getNombre(), user.getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteHorse(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        stableService.deleteHorse(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Caballo eliminado del establo")));
    }

    @PatchMapping("/{id}/sell")
    public ResponseEntity<ApiResponse<Object>> putForSale(
            @PathVariable Long id,
            @RequestBody SellRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(stableService.putForSale(id, request.getPrecio(), user.getId())));
    }

    @PatchMapping("/{id}/unsell")
    public ResponseEntity<ApiResponse<Object>> removeFromSale(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        stableService.removeFromSale(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Caballo removido de la venta")));
    }
}
