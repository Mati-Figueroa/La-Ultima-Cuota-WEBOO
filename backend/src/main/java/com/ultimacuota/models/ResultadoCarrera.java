package com.ultimacuota.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "resultados_carrera",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"carrera_id", "caballo_id"}),
           @UniqueConstraint(columnNames = {"carrera_id", "posicion_final"})
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResultadoCarrera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrera_id", nullable = false)
    private Carrera carrera;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caballo_id", nullable = false)
    private Caballo caballo;

    @Column(name = "posicion_final", nullable = false)
    private Integer posicionFinal;

    @Column(name = "tiempo_final", precision = 8, scale = 2)
    private BigDecimal tiempoFinal;
}
