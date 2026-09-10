package com.ultimacuota.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InscribeRequest {
    @JsonAlias({"caballo_id", "caballoId"})
    private Long caballoId;

    public void setCaballo_id(Long id) {
        this.caballoId = id;
    }
}
