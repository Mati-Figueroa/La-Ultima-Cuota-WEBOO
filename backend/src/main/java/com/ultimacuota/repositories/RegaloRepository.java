package com.ultimacuota.repositories;

import com.ultimacuota.models.Regalo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegaloRepository extends JpaRepository<Regalo, Long> {

    @Query("SELECT r FROM Regalo r JOIN FETCH r.remitente JOIN FETCH r.destinatario LEFT JOIN FETCH r.item " +
           "WHERE r.destinatario.id = :userId ORDER BY r.createdAt DESC, r.id DESC")
    List<Regalo> findRecibidos(@Param("userId") Long userId);

    @Query("SELECT r FROM Regalo r JOIN FETCH r.remitente JOIN FETCH r.destinatario LEFT JOIN FETCH r.item " +
           "WHERE r.remitente.id = :userId ORDER BY r.createdAt DESC, r.id DESC")
    List<Regalo> findEnviados(@Param("userId") Long userId);
}
