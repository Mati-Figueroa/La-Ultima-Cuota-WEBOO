package com.ultimacuota.repositories;

import com.ultimacuota.models.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    @Query("SELECT i FROM Inscripcion i " +
           "JOIN FETCH i.caballo c " +
           "LEFT JOIN FETCH i.usuario u " +
           "WHERE i.carrera.id = :carreraId " +
           "ORDER BY i.numeroCarril ASC NULLS LAST")
    List<Inscripcion> findByCarreraIdWithCaballo(@Param("carreraId") Long carreraId);

    long countByCarreraId(Long carreraId);

    Optional<Inscripcion> findByCarreraIdAndCaballoId(Long carreraId, Long caballoId);

    boolean existsByCarreraIdAndCaballoId(Long carreraId, Long caballoId);

    @Query("SELECT COALESCE(MAX(i.numeroCarril), 0) + 1 FROM Inscripcion i WHERE i.carrera.id = :carreraId")
    int findNextLane(@Param("carreraId") Long carreraId);
}
