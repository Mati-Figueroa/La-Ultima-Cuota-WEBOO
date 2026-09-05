package com.ultimacuota.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subastas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subasta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caballo_id", nullable = false)
    private Caballo caballo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Usuario vendedor;

    @Column(name = "precio_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioInicial;

    @Column(name = "precio_reserva", precision = 12, scale = 2)
    private BigDecimal precioReserva;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String estado = "activa";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ganador_id")
    private Usuario ganador;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
