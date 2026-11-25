package com.ultimacuota.repositories;

import com.ultimacuota.models.Inventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioRepository extends JpaRepository<Inventario, Long> {

    @Query("SELECT i FROM Inventario i JOIN FETCH i.item WHERE i.usuario.id = :userId AND i.cantidad > 0 ORDER BY i.item.categoria, i.item.precio")
    List<Inventario> findByUsuarioConItems(@Param("userId") Long userId);

    Optional<Inventario> findByUsuarioIdAndItemId(Long usuarioId, Long itemId);
}
