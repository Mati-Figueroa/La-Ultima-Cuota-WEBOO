package com.ultimacuota.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventario", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "item_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Inventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ItemTienda item;

    @Builder.Default
    @Column(nullable = false)
    private Integer cantidad = 0;
}
