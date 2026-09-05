package com.ultimacuota.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAuctionRequest {
    private Long caballoId;
    private BigDecimal precioInicial;
    private BigDecimal precioReserva;
    private Long duracionHoras; // 1, 6, 24, 48, 72
}
