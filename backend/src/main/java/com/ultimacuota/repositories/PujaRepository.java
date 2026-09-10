package com.ultimacuota.repositories;

import com.ultimacuota.models.Puja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PujaRepository extends JpaRepository<Puja, Long> {

    @Query("SELECT p FROM Puja p JOIN FETCH p.usuario WHERE p.subasta.id = :subastaId ORDER BY p.monto DESC, p.fecha ASC")
    List<Puja> findBySubastaIdOrderByMontoDescFechaAsc(@Param("subastaId") Long subastaId);

    Optional<Puja> findBySubastaIdAndUsuarioIdOrderByMontoDesc(Long subastaId, Long usuarioId);

    @Query("SELECT p FROM Puja p JOIN FETCH p.usuario WHERE p.subasta.id = :subastaId ORDER BY p.monto DESC LIMIT 1")
    Optional<Puja> findHighestBid(@Param("subastaId") Long subastaId);

    @Query("SELECT COUNT(p) FROM Puja p WHERE p.subasta.id = :subastaId")
    long countBidsByAuction(@Param("subastaId") Long subastaId);

    @Modifying
    @Query("UPDATE Puja p SET p.esGanadora = true WHERE p.id = :id")
    int markAsWinner(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Puja p SET p.esGanadora = false WHERE p.subasta.id = :subastaId")
    int clearWinners(@Param("subastaId") Long subastaId);

    @Query("SELECT p FROM Puja p WHERE p.usuario.id = :userId ORDER BY p.fecha DESC")
    List<Puja> findByUsuarioIdOrderByFechaDesc(@Param("userId") Long userId);
}
