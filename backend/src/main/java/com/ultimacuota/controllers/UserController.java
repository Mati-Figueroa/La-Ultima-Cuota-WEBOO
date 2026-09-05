package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("user", userService.getUserProfile(id))));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Object>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("users", userService.searchUsers(q))));
    }
}
