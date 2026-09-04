package com.ultimacuota.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "caballos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Caballo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_id")
    private Usuario propietario;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private Integer edad;

    @Column(nullable = false)
    private Integer velocidad;

    @Column(nullable = false)
    private Integer resistencia;

    @Column(nullable = false)
    private Integer corazon;

    @Builder.Default
    private Integer fatiga = 0;

    @Builder.Default
    @Column(name = "carreras_totales")
    private Integer carrerasTotales = 0;

    @Builder.Default
    private Integer victorias = 0;

    @Column(name = "posicion_promedio", precision = 4, scale = 2)
    private BigDecimal posicionPromedio;

    @Builder.Default
    @Column(name = "en_venta")
    private Boolean enVenta = false;

    @Column(name = "precio_venta", precision = 12, scale = 2)
    private BigDecimal precioVenta;

    @Builder.Default
    @Column(name = "es_bot")
    private Boolean esBot = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
