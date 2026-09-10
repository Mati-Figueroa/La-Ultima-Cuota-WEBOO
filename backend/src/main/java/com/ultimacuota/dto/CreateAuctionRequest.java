package com.ultimacuota.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAuctionRequest {
    @JsonAlias({"caballo_id", "caballoId"})
    private Long caballoId;

    @JsonAlias({"precio_inicial", "precioInicial"})
    private BigDecimal precioInicial;

    @JsonAlias({"precio_reserva", "precioReserva"})
    private BigDecimal precioReserva;

    @JsonAlias({"duracion_horas", "duracionHoras"})
    private Long duracionHoras; // 1, 6, 24, 48, 72

    public void setCaballo_id(Long id) { this.caballoId = id; }
    public void setPrecio_inicial(BigDecimal p) { this.precioInicial = p; }
    public void setPrecio_reserva(BigDecimal p) { this.precioReserva = p; }
    public void setDuracion_horas(Long d) { this.duracionHoras = d; }
}
