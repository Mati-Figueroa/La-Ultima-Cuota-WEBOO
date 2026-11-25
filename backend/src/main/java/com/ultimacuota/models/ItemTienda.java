package com.ultimacuota.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "items_tienda")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ItemTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, length = 20)
    private String categoria;

    @Column(nullable = false, length = 20)
    private String calidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Builder.Default
    @Column(name = "cura_nivel")
    private Integer curaNivel = 0;

    @Builder.Default
    @Column(name = "reduce_fatiga")
    private Integer reduceFatiga = 0;

    @Builder.Default
    @Column(name = "aumenta_felicidad")
    private Integer aumentaFelicidad = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
