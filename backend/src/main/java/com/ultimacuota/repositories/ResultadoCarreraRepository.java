package com.ultimacuota.repositories;

import com.ultimacuota.models.ResultadoCarrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultadoCarreraRepository extends JpaRepository<ResultadoCarrera, Long> {

    List<ResultadoCarrera> findByCarreraIdOrderByPosicionFinalAsc(Long carreraId);

    boolean existsByCarreraId(Long carreraId);
}
