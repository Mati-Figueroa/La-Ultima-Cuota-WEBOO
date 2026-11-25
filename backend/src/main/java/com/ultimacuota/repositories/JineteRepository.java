package com.ultimacuota.repositories;

import com.ultimacuota.models.Jinete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JineteRepository extends JpaRepository<Jinete, Long> {

    @Query("SELECT j FROM Jinete j LEFT JOIN FETCH j.empleador LEFT JOIN FETCH j.caballo ORDER BY j.habilidad DESC")
    List<Jinete> findAllWithRelations();

    @Query("SELECT j FROM Jinete j WHERE j.empleador IS NULL ORDER BY j.habilidad DESC")
    List<Jinete> findDisponibles();

    @Query("SELECT j FROM Jinete j LEFT JOIN FETCH j.caballo WHERE j.empleador.id = :userId ORDER BY j.habilidad DESC")
    List<Jinete> findByEmpleadorId(@Param("userId") Long userId);

    long countByEmpleadorId(Long userId);

    Optional<Jinete> findByCaballoId(Long caballoId);

    /** Jinete asignado al caballo y contratado por su dueño actual (si no, no cuenta). */
    @Query("SELECT j FROM Jinete j WHERE j.caballo.id = :caballoId AND j.empleador IS NOT NULL " +
           "AND j.empleador.id = j.caballo.propietario.id")
    Optional<Jinete> findValidoPorCaballo(@Param("caballoId") Long caballoId);

    @Modifying
    @Query("UPDATE Jinete j SET j.caballo = NULL WHERE j.caballo.id = :caballoId")
    int desasignarDeCaballo(@Param("caballoId") Long caballoId);

    /** +1 de experiencia; cada 10 carreras, +1 de habilidad (máx. 100). */
    @Modifying
    @Query("UPDATE Jinete j SET j.experiencia = COALESCE(j.experiencia, 0) + 1, " +
           "j.habilidad = CASE WHEN MOD(COALESCE(j.experiencia, 0) + 1, 10) = 0 THEN LEAST(j.habilidad + 1, 100) ELSE j.habilidad END " +
           "WHERE j.id = :id")
    int sumarExperiencia(@Param("id") Long id);
}
