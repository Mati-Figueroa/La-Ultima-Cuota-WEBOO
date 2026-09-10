package com.ultimacuota.repositories;

import com.ultimacuota.models.Subasta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubastaRepository extends JpaRepository<Subasta, Long> {

    List<Subasta> findByEstadoOrderByFechaFinAsc(String estado);

    List<Subasta> findByVendedorIdOrderByCreatedAtDesc(Long vendedorId);

    List<Subasta> findByGanadorIdOrderByCreatedAtDesc(Long ganadorId);

    @Query("SELECT s FROM Subasta s WHERE s.estado = 'activa' AND s.fechaFin <= :now")
    List<Subasta> findExpiredAuctions(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Subasta s WHERE s.estado = 'activa' ORDER BY s.fechaFin ASC")
    List<Subasta> findActiveAuctions();

    @Modifying
    @Query(value = "UPDATE subastas SET estado = :estado WHERE id = :id", nativeQuery = true)
    int updateEstado(@Param("id") Long id, @Param("estado") String estado);

    @Modifying
    @Query(value = "UPDATE subastas SET estado = 'finalizada', ganador_id = :ganadorId WHERE id = :id", nativeQuery = true)
    int finalizeWithWinner(@Param("id") Long id, @Param("ganadorId") Long ganadorId);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Subasta s " +
           "WHERE s.caballo.id = :caballoId AND s.estado = 'activa'")
    boolean isHorseInActiveAuction(@Param("caballoId") Long caballoId);
}
