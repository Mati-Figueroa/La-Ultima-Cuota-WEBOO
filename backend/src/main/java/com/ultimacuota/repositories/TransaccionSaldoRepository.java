package com.ultimacuota.repositories;

import com.ultimacuota.models.TransaccionSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransaccionSaldoRepository extends JpaRepository<TransaccionSaldo, Long> {

    @Query("SELECT t FROM TransaccionSaldo t " +
           "JOIN FETCH t.usuario u " +
           "WHERE t.usuario.id = :usuarioId AND t.tipo = :tipo " +
           "ORDER BY t.createdAt DESC")
    List<TransaccionSaldo> findByUsuarioIdAndTipo(@Param("usuarioId") Long usuarioId, @Param("tipo") String tipo);

    @Query("SELECT t FROM TransaccionSaldo t " +
           "JOIN FETCH t.usuario u " +
           "WHERE t.usuario.id = :usuarioId AND t.tipo = 'apuesta_ganada' " +
           "ORDER BY t.createdAt DESC")
    List<TransaccionSaldo> findRecentWins(@Param("usuarioId") Long usuarioId);
}
