package com.ultimacuota.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GiftRequest {
    @JsonAlias({"destinatario_id", "destinatarioId"})
    private Long destinatarioId;

    private BigDecimal monto;

    @JsonAlias({"item_id", "itemId"})
    private Long itemId;

    private Integer cantidad;

    private String mensaje;
}
