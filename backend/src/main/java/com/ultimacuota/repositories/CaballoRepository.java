package com.ultimacuota.repositories;

import com.ultimacuota.models.Caballo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CaballoRepository extends JpaRepository<Caballo, Long> {

    List<Caballo> findByPropietarioIdOrderByCreatedAtDesc(Long propietarioId);

    @Query("SELECT c FROM Caballo c WHERE c.enVenta = true AND c.propietario IS NOT NULL " +
           "AND (:search IS NULL OR :search = '' OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY " +
           "CASE WHEN :sort = 'price_desc' THEN c.precioVenta END DESC NULLS LAST, " +
           "CASE WHEN :sort = 'wins' THEN c.victorias END DESC NULLS LAST, " +
           "c.precioVenta ASC")
    List<Caballo> findOnSaleWithSearch(@Param("search") String search, @Param("sort") String sort);

    @Modifying
    @Query("UPDATE Caballo c SET c.nombre = :nombre WHERE c.id = :id AND c.propietario.id = :propietarioId")
    int updateName(@Param("id") Long id, @Param("nombre") String nombre, @Param("propietarioId") Long propietarioId);

    @Modifying
    @Query("UPDATE Caballo c SET c.enVenta = true, c.precioVenta = :precio WHERE c.id = :id AND c.propietario.id = :propietarioId AND c.esBot = false")
    int setForSale(@Param("id") Long id, @Param("precio") BigDecimal precio, @Param("propietarioId") Long propietarioId);

    @Modifying
    @Query("UPDATE Caballo c SET c.enVenta = false, c.precioVenta = NULL WHERE c.id = :id AND c.propietario.id = :propietarioId")
    int removeFromSale(@Param("id") Long id, @Param("propietarioId") Long propietarioId);

    @Modifying
    @Query(value = "UPDATE caballos SET propietario_id = :newOwnerId, en_venta = false, precio_venta = NULL WHERE id = :id", nativeQuery = true)
    int transfer(@Param("id") Long id, @Param("newOwnerId") Long newOwnerId);

    @Modifying
    @Query("DELETE FROM Caballo c WHERE c.id = :id AND c.propietario.id = :propietarioId")
    int deleteByIdAndPropietarioId(@Param("id") Long id, @Param("propietarioId") Long propietarioId);

    @Query(value = "SELECT c FROM Caballo c WHERE c.esBot = true AND c.id NOT IN :excludeIds ORDER BY FUNCTION('RANDOM')")
    List<Caballo> findBotHorsesForRace(@Param("excludeIds") List<Long> excludeIds);

    @Query(value = "SELECT c FROM Caballo c WHERE c.esBot = true ORDER BY FUNCTION('RANDOM')")
    List<Caballo> findRandomBotHorses();

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END " +
           "FROM Inscripcion i JOIN i.carrera cr " +
           "WHERE i.caballo.id = :caballoId AND cr.estado IN ('programada', 'en_curso')")
    boolean isInscribedInActiveRace(@Param("caballoId") Long caballoId);

    @Modifying
    @Query("UPDATE Caballo c SET c.carrerasTotales = c.carrerasTotales + :races, " +
           "c.victorias = c.victorias + :wins, " +
           "c.posicionPromedio = CASE " +
           "  WHEN c.posicionPromedio IS NULL THEN :avgPos " +
           "  ELSE ROUND((c.posicionPromedio * c.carrerasTotales + :avgPos) / (c.carrerasTotales + 1), 2) " +
           "END, " +
           "c.fatiga = LEAST(c.fatiga + 15, 100) " +
           "WHERE c.id = :id")
    void incrementStats(@Param("id") Long id, @Param("wins") int wins, @Param("races") int races, @Param("avgPos") double avgPos);
}
