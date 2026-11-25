package com.ultimacuota.repositories;

import com.ultimacuota.models.EventoCarrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventoCarreraRepository extends JpaRepository<EventoCarrera, Long> {

    @Query("SELECT e FROM EventoCarrera e LEFT JOIN FETCH e.caballo WHERE e.carrera.id = :carreraId ORDER BY e.segundo ASC, e.id ASC")
    List<EventoCarrera> findByCarreraIdOrdenados(@Param("carreraId") Long carreraId);
}
