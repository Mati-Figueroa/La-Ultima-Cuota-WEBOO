package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.dto.GiftRequest;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.GiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/gifts")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> enviar(@RequestBody GiftRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(giftService.enviar(request, user.getId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> listar(@RequestParam(defaultValue = "recibidos") String tipo,
                                                      @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("regalos", giftService.listar(user.getId(), tipo))));
    }
}
