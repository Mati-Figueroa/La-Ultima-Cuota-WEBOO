package com.ultimacuota.repositories;

import com.ultimacuota.models.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CarreraRepository extends JpaRepository<Carrera, Long> {

    @Query("SELECT c FROM Carrera c WHERE :estado IS NULL OR c.estado = :estado ORDER BY c.fechaProgramada ASC")
    List<Carrera> findAllByEstado(@Param("estado") String estado);

    @Query("SELECT c FROM Carrera c WHERE c.estado = :estado AND c.fechaProgramada <= :now")
    List<Carrera> findToTransition(@Param("estado") String estado, @Param("now") LocalDateTime now);

    @Query("SELECT c FROM Carrera c WHERE c.estado = 'en_curso' AND c.fechaInicioReal IS NOT NULL")
    List<Carrera> findRunningWithStartTime();

    @Modifying
    @Query("DELETE FROM Carrera c WHERE c.estado = 'programada' AND c.tieneInteraccionHumana = false AND c.fechaProgramada < :now")
    int deleteExpiredBotOnlyRaces(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM Carrera c WHERE c.estado = 'programada' AND c.tieneInteraccionHumana = false")
    int deleteAllBotOnlyProgrammedRaces();

    @Query("SELECT COUNT(c) FROM Carrera c WHERE c.estado = 'programada' AND c.fechaProgramada > :now")
    long countUpcomingProgrammed(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(c) FROM Carrera c WHERE c.estado = 'programada'")
    long countProgrammed();
}
