package com.ultimacuota.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "jinetes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Jinete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false)
    private Integer habilidad;

    @Column(nullable = false)
    private Integer peso;

    @Builder.Default
    private Integer experiencia = 0;

    @Column(length = 20)
    private String especialidad;

    @Column(name = "precio_contratacion", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioContratacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleador_id")
    private Usuario empleador;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caballo_id", unique = true)
    private Caballo caballo;

    @Column(name = "fecha_contratacion")
    private LocalDateTime fechaContratacion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
