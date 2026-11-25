package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.dto.AssignJockeyRequest;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.JockeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/jockeys")
@RequiredArgsConstructor
public class JockeyController {

    private final JockeyService jockeyService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> listar(@RequestParam(defaultValue = "todos") String estado) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("jinetes", jockeyService.listar(estado))));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<Object>> mios(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("jinetes", jockeyService.mios(user.getId()))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("jinete", jockeyService.obtener(id))));
    }

    @PostMapping("/{id}/hire")
    public ResponseEntity<ApiResponse<Object>> contratar(@PathVariable Long id,
                                                         @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(jockeyService.contratar(id, user.getId())));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<Object>> asignar(@PathVariable Long id,
                                                       @RequestBody(required = false) AssignJockeyRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails user) {
        Long caballoId = request != null ? request.getCaballoId() : null;
        return ResponseEntity.ok(ApiResponse.ok(jockeyService.asignar(id, caballoId, user.getId())));
    }

    @PostMapping("/{id}/fire")
    public ResponseEntity<ApiResponse<Object>> despedir(@PathVariable Long id,
                                                        @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(jockeyService.despedir(id, user.getId())));
    }
}
