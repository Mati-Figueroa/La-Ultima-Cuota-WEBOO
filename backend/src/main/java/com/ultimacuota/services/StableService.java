package com.ultimacuota.services;

import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.Caballo;
import com.ultimacuota.repositories.ApuestaRepository;
import com.ultimacuota.repositories.CaballoRepository;
import com.ultimacuota.repositories.InscripcionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StableService {

    private final CaballoRepository caballoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final ApuestaRepository apuestaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Map<String, Object>> getMyHorses(Long userId) {
        return caballoRepository.findByPropietarioIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toHorseMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getHorseHistory(Long horseId, Long userId) {
        Caballo horse = caballoRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado"));
        if (horse.getPropietario() == null || !horse.getPropietario().getId().equals(userId)) {
            throw new ResourceNotFoundException("Caballo no encontrado");
        }

        List<Object[]> results = entityManager.createNativeQuery(
                "SELECT c.id AS carrera_id, c.nombre AS carrera_nombre, c.fecha_programada, c.estado, " +
                "rc.posicion_final, rc.tiempo_final " +
                "FROM inscripciones i " +
                "JOIN carreras c ON c.id = i.carrera_id " +
                "LEFT JOIN resultados_carrera rc ON rc.carrera_id = c.id AND rc.caballo_id = i.caballo_id " +
                "WHERE i.caballo_id = :horseId " +
                "ORDER BY c.fecha_programada DESC")
                .setParameter("horseId", horseId)
                .getResultList();

        return results.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("carrera_id", ((Number) row[0]).longValue());
            map.put("carrera_nombre", row[1]);
            map.put("fecha_programada", row[2]);
            map.put("estado", row[3]);
            map.put("posicion_final", row[4] != null ? ((Number) row[4]).intValue() : null);
            map.put("tiempo_final", row[5]);
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> renameHorse(Long horseId, String nombre, Long userId) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (nombre.trim().length() > 100) {
            throw new IllegalArgumentException("El nombre no puede exceder 100 caracteres");
        }

        int updated = caballoRepository.updateName(horseId, nombre.trim(), userId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Caballo no encontrado");
        }

        Caballo horse = caballoRepository.findById(horseId).orElseThrow();
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> horseMap = new HashMap<>();
        horseMap.put("id", horse.getId());
        horseMap.put("nombre", horse.getNombre());
        result.put("caballo", horseMap);
        return result;
    }

    @Transactional
    public void deleteHorse(Long horseId, Long userId) {
        Caballo horse = caballoRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado"));
        if (horse.getPropietario() == null || !horse.getPropietario().getId().equals(userId)) {
            throw new ResourceNotFoundException("Caballo no encontrado");
        }

        if (caballoRepository.isInscribedInActiveRace(horseId)) {
            throw new IllegalArgumentException("No puedes eliminar un caballo inscrito en una carrera activa");
        }

        caballoRepository.deleteByIdAndPropietarioId(horseId, userId);
    }

    @Transactional
    public Map<String, Object> putForSale(Long horseId, java.math.BigDecimal precio, Long userId) {
        if (precio == null || precio.doubleValue() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a 0");
        }

        Caballo horse = caballoRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado"));
        if (horse.getPropietario() == null || !horse.getPropietario().getId().equals(userId)) {
            throw new ResourceNotFoundException("Caballo no encontrado");
        }
        if (horse.getEsBot()) {
            throw new IllegalArgumentException("Los caballos bot no se pueden vender");
        }
        if (caballoRepository.isInscribedInActiveRace(horseId)) {
            throw new IllegalArgumentException("No puedes vender un caballo inscrito en una carrera activa");
        }

        caballoRepository.setForSale(horseId, precio, userId);
        entityManager.clear();

        horse = caballoRepository.findById(horseId).orElseThrow();
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> horseMap = new HashMap<>();
        horseMap.put("id", horse.getId());
        horseMap.put("en_venta", horse.getEnVenta());
        horseMap.put("precio_venta", horse.getPrecioVenta());
        result.put("caballo", horseMap);
        return result;
    }

    @Transactional
    public void removeFromSale(Long horseId, Long userId) {
        int updated = caballoRepository.removeFromSale(horseId, userId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Caballo no encontrado");
        }
    }

    private Map<String, Object> toHorseMap(Caballo c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("nombre", c.getNombre());
        map.put("edad", c.getEdad());
        map.put("fatiga", c.getFatiga());
        map.put("carreras_totales", c.getCarrerasTotales());
        map.put("victorias", c.getVictorias());
        map.put("posicion_promedio", c.getPosicionPromedio());
        map.put("en_venta", c.getEnVenta());
        map.put("precio_venta", c.getPrecioVenta());
        map.put("created_at", c.getCreatedAt());
        return map;
    }
}
