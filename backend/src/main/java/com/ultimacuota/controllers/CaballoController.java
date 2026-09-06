package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.services.CaballoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/horses")
@RequiredArgsConstructor
public class CaballoController {

    private final CaballoService caballoService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getHorseById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("horse", caballoService.getHorseById(id))));
    }
}
