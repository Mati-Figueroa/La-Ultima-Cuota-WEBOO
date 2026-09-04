package com.ultimacuota.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private BigDecimal saldo;
    private LocalDateTime createdAt;

    public static UserResponse from(com.ultimacuota.models.Usuario u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getSaldo(), u.getCreatedAt());
    }
}
