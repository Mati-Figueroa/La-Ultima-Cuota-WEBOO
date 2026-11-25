package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.dto.BuyItemRequest;
import com.ultimacuota.dto.UseItemRequest;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @GetMapping("/shop/items")
    public ResponseEntity<ApiResponse<Object>> listarItems() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("items", shopService.listarItems())));
    }

    @PostMapping("/shop/items/{id}/buy")
    public ResponseEntity<ApiResponse<Object>> comprar(@PathVariable Long id,
                                                       @RequestBody(required = false) BuyItemRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails user) {
        Integer cantidad = request != null ? request.getCantidad() : null;
        return ResponseEntity.ok(ApiResponse.ok(shopService.comprar(id, cantidad, user.getId())));
    }

    @GetMapping("/inventory")
    public ResponseEntity<ApiResponse<Object>> inventario(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("inventario", shopService.inventario(user.getId()))));
    }

    @PostMapping("/inventory/{itemId}/use")
    public ResponseEntity<ApiResponse<Object>> usar(@PathVariable Long itemId,
                                                    @RequestBody UseItemRequest request,
                                                    @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(shopService.usar(itemId, request.getCaballoId(), user.getId())));
    }
}
