package com.ultimacuota.services;

import com.ultimacuota.models.Caballo;
import com.ultimacuota.repositories.CaballoRepository;
import com.ultimacuota.repositories.ConfiguracionRepository;
import com.ultimacuota.repositories.JineteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/** Felicidad, lesiones y efectos de una carrera sobre los caballos de los usuarios. */
@Service
@RequiredArgsConstructor
@Slf4j
public class HorseConditionService {

    public static final int FELICIDAD_MINIMA_DECAIMIENTO = 20;

    private final CaballoRepository caballoRepository;
    private final JineteRepository jineteRepository;
    private final ConfiguracionRepository configuracionRepository;

    private final Random random = new Random();

    /**
     * Aplica felicidad, lesiones, fatiga extra y experiencia de jinete tras una carrera.
     * Usa updates atómicos porque {@code incrementStats} ya modificó la fatiga en bloque.
     */
    @Transactional
    public void aplicarResultadoCarrera(List<Map<String, Object>> results) {
        int total = results.size();
        for (Map<String, Object> r : results) {
            Long caballoId = (Long) r.get("caballo_id");
            int posicion = (int) r.get("posicion");
            boolean abandono = Boolean.TRUE.equals(r.get("abandono"));

            Caballo caballo = caballoRepository.findById(caballoId).orElse(null);
            if (caballo == null || Boolean.TRUE.equals(caballo.getEsBot())) continue;

            int delta = numero(r.get("delta_felicidad"));
            if (!abandono) {
                if (posicion == 1) delta += 10;
                else if (posicion <= 3 && total > 3) delta += 3;
                else delta -= 3;
            }
            caballoRepository.ajustarCondicionPostCarrera(caballoId, delta, numero(r.get("fatiga_extra")));

            String lesion = (String) r.get("lesion_pendiente");
            if (lesion == null) {
                int fatiga = caballo.getFatiga() != null ? caballo.getFatiga() : 0;
                if (random.nextDouble() < Math.min(0.10, fatiga / 1000.0)) lesion = RaceEventEngine.LESION_LEVE;
            }
            if (lesion != null) {
                aplicarLesion(caballoId, lesion);
            }

            jineteRepository.findValidoPorCaballo(caballoId)
                    .ifPresent(j -> jineteRepository.sumarExperiencia(j.getId()));
        }
    }

    /** Aplica una lesión si es más grave que la actual. Devuelve true si se aplicó. */
    @Transactional
    public boolean aplicarLesion(Long caballoId, String lesion) {
        int nivel = RaceEventEngine.nivelLesion(lesion);
        if (nivel == 0) return false;
        List<String> menores = new ArrayList<>();
        if (nivel > 1) menores.add(RaceEventEngine.LESION_LEVE);
        if (nivel > 2) menores.add(RaceEventEngine.LESION_MODERADA);
        if (menores.isEmpty()) menores.add("__ninguna__");
        return caballoRepository.aplicarLesion(caballoId, lesion, calcularLesionHasta(lesion, LocalDateTime.now()), menores) > 0;
    }

    /** Cuándo sana sola una lesión; null para la grave (solo sana con un ítem). */
    public LocalDateTime calcularLesionHasta(String lesion, LocalDateTime desde) {
        return switch (RaceEventEngine.nivelLesion(lesion)) {
            case 1 -> desde.plusMinutes(config("lesion_leve_minutos", 60));
            case 2 -> desde.plusMinutes(config("lesion_moderada_minutos", 360));
            default -> null;
        };
    }

    @Transactional
    public int sanarLesionesVencidas() {
        return caballoRepository.sanarLesionesVencidas(LocalDateTime.now());
    }

    @Transactional
    public int decaerFelicidad(int cantidad) {
        return caballoRepository.decaerFelicidad(cantidad, FELICIDAD_MINIMA_DECAIMIENTO);
    }

    public int felicidadMinimaParaCorrer() {
        return config("felicidad_minima_carrera", 10);
    }

    private int config(String clave, int porDefecto) {
        return configuracionRepository.findByClave(clave)
                .map(c -> {
                    try { return Integer.parseInt(c.getValor().trim()); } catch (Exception e) { return porDefecto; }
                })
                .orElse(porDefecto);
    }

    private static int numero(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    /** Campos de condición para los mapas JSON de un caballo. */
    public static void agregarCondicion(Map<String, Object> map, Caballo c) {
        map.put("felicidad", c.getFelicidad() != null ? c.getFelicidad() : 70);
        map.put("lesion", c.getLesion());
        map.put("lesion_hasta", c.getLesionHasta());
    }
}
