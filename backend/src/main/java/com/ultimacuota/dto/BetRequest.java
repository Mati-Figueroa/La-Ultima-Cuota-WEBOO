package com.ultimacuota.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BetRequest {
    @JsonAlias({"caballo_id", "caballoId"})
    private Long caballoId;

    private BigDecimal monto;

    public void setCaballo_id(Long id) {
        this.caballoId = id;
    }
}
