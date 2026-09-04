package com.ultimacuota.services;

import com.ultimacuota.models.Apuesta;
import com.ultimacuota.models.TransaccionSaldo;
import com.ultimacuota.repositories.ApuestaRepository;
import com.ultimacuota.repositories.TransaccionSaldoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ApuestaRepository apuestaRepository;
    private final TransaccionSaldoRepository transaccionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public Map<String, Object> getMyBets(Long userId, String estado, int page, int limit) {
        PageRequest pageable = PageRequest.of(page - 1, limit);
        Page<Apuesta> betPage = apuestaRepository.findByUsuarioIdWithEstado(userId, estado, pageable);

        List<Map<String, Object>> bets = betPage.getContent().stream().map(a -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("monto", a.getMonto());
            map.put("cuota", a.getCuota());
            map.put("estado", a.getEstado());
            map.put("monto_ganado", a.getMontoGanado());
            map.put("created_at", a.getCreatedAt());
            map.put("carrera_nombre", a.getCarrera().getNombre());
            map.put("carrera_estado", a.getCarrera().getEstado());
            map.put("caballo_nombre", a.getCaballo().getNombre());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", betPage.getTotalElements());
        pagination.put("pages", betPage.getTotalPages());

        Map<String, Object> result = new HashMap<>();
        result.put("bets", bets);
        result.put("pagination", pagination);
        return result;
    }

    public List<Map<String, Object>> getMyWins(Long userId) {
        List<TransaccionSaldo> wins = transaccionRepository.findRecentWins(userId);
        return wins.stream().limit(5).map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("ganancia", t.getMonto());
            map.put("created_at", t.getCreatedAt());
            map.put("carrera_id", t.getReferenciaId());
            return map;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getStats(Long userId) {
        Object[] stats = (Object[]) entityManager.createNativeQuery(
                "SELECT " +
                "COUNT(*) AS total_apuestas, " +
                "COUNT(*) FILTER (WHERE estado = 'ganada') AS apuestas_ganadas, " +
                "COALESCE(SUM(monto_ganado) FILTER (WHERE estado = 'ganada'), 0) AS total_ganado, " +
                "COALESCE(SUM(monto) FILTER (WHERE estado = 'perdida'), 0) AS total_perdido " +
                "FROM apuestas WHERE usuario_id = :userId")
                .setParameter("userId", userId)
                .getSingleResult();

        Map<String, Object> statsMap = new HashMap<>();
        statsMap.put("total_apuestas", ((Number) stats[0]).longValue());
        statsMap.put("apuestas_ganadas", ((Number) stats[1]).longValue());
        statsMap.put("total_ganado", stats[2]);
        statsMap.put("total_perdido", stats[3]);

        Map<String, Object> result = new HashMap<>();
        result.put("stats", statsMap);
        return result;
    }
}
