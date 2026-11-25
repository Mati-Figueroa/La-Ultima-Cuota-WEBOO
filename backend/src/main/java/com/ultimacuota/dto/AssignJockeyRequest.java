package com.ultimacuota.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignJockeyRequest {
    /** null para quitar al jinete de su caballo. */
    @JsonAlias({"caballo_id", "caballoId"})
    private Long caballoId;
}
