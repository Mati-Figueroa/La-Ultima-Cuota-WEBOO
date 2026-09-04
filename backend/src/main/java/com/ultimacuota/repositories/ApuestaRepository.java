package com.ultimacuota.repositories;

import com.ultimacuota.models.Apuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApuestaRepository extends JpaRepository<Apuesta, Long> {

    @Query("SELECT a FROM Apuesta a " +
           "JOIN FETCH a.caballo c " +
           "LEFT JOIN FETCH a.usuario u " +
           "WHERE a.carrera.id = :carreraId AND a.estado = 'pendiente'")
    List<Apuesta> findPendingByCarreraId(@Param("carreraId") Long carreraId);

    @Query("SELECT a FROM Apuesta a " +
           "JOIN FETCH a.carrera cr " +
           "JOIN FETCH a.caballo c " +
           "WHERE a.usuario.id = :usuarioId " +
           "ORDER BY a.createdAt DESC")
    List<Apuesta> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query(value = "SELECT a FROM Apuesta a " +
           "JOIN FETCH a.carrera cr " +
           "JOIN FETCH a.caballo c " +
           "WHERE a.usuario.id = :usuarioId " +
           "AND (:estado IS NULL OR a.estado = :estado) " +
           "ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM Apuesta a WHERE a.usuario.id = :usuarioId AND (:estado IS NULL OR a.estado = :estado)")
    org.springframework.data.domain.Page<Apuesta> findByUsuarioIdWithEstado(
            @Param("usuarioId") Long usuarioId,
            @Param("estado") String estado,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT a FROM Apuesta a " +
           "JOIN FETCH a.carrera cr " +
           "WHERE a.carrera.id = :carreraId AND a.estado = 'pendiente'")
    List<Apuesta> findPendingBetsWithCarrera(@Param("carreraId") Long carreraId);
}
