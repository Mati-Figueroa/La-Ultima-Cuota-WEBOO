package com.ultimacuota.scheduler;

import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import com.ultimacuota.services.BetSettlementService;
import com.ultimacuota.services.RaceSimulationService;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class RaceLifecycleManager {

    private final CarreraRepository carreraRepository;
    private final InscripcionRepository inscripcionRepository;
    private final CaballoRepository caballoRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final ResultadoCarreraRepository resultadoRepository;
    private final BetSettlementService betSettlementService;
    private final RaceCreator raceCreator;
    private final RaceSimulationService simulationService;

    @PersistenceContext
    private EntityManager entityManager;

    private SocketIOServer socketIOServer;
    private final Set<Long> processedRaceIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> settlingRaces = ConcurrentHashMap.newKeySet();

    public void setSocketIOServer(SocketIOServer server) {
        this.socketIOServer = server;
    }

    public Set<Long> getProcessedRaceIds() {
        return processedRaceIds;
    }

    @Transactional
    public void transitionRaces() {
        try {
            LocalDateTime now = LocalDateTime.now();

            carreraRepository.deleteExpiredBotOnlyRaces(now);

            List<Carrera> toStart = carreraRepository.findToTransition("programada", now);
            for (Carrera race : toStart) {
                race.setEstado("en_curso");
                race.setFechaInicioReal(now);
                carreraRepository.save(race);

                List<Inscripcion> existing = inscripcionRepository.findByCarreraIdWithCaballo(race.getId());
                if (existing.size() < 6) {
                    raceCreator.fillWithBots(race.getId());
                }

                processedRaceIds.add(race.getId());
                simulationService.startSimulation(race.getId());

                if (socketIOServer != null) {
                    socketIOServer.getBroadcastOperations().sendEvent("race_started",
                            Map.of("carrera_id", race.getId()));
                }
                log.info("[Lifecycle] Carrera #{} iniciada", race.getId());
            }

            List<Carrera> toFinish = carreraRepository.findRunningWithStartTime();
            BigDecimal raceDuration = configuracionRepository.findByClave("race_duration_seconds")
                    .map(c -> new BigDecimal(c.getValor()))
                    .orElse(BigDecimal.valueOf(30));

            for (Carrera race : toFinish) {
                if (simulationService.isRunning(race.getId())) continue;

                long elapsed = java.time.Duration.between(race.getFechaInicioReal(), now).getSeconds();
                if (elapsed < raceDuration.longValue()) continue;

                settleRace(race.getId(), null);
            }
        } catch (Exception e) {
            log.error("[Lifecycle] Error en transiciones: {}", e.getMessage());
        }
    }

    @Transactional
    public void settleRace(Long raceId, List<Map<String, Object>> finishOrder) {
        if (settlingRaces.contains(raceId)) return;
        settlingRaces.add(raceId);

        try {
            Carrera race = carreraRepository.findById(raceId).orElse(null);
            if (race == null) return;

            if (resultadoRepository.existsByCarreraId(raceId)) {
                if (!"finalizada".equals(race.getEstado())) {
                    race.setEstado("finalizada");
                    race.setFechaFinReal(LocalDateTime.now());
                    carreraRepository.save(race);
                }
                return;
            }

            List<Inscripcion> inscriptions = inscripcionRepository.findByCarreraIdWithCaballo(raceId);
            if (inscriptions.isEmpty()) {
                carreraRepository.delete(race);
                log.info("[Lifecycle] Carrera #{} eliminada (sin inscripciones)", raceId);
                return;
            }

            List<Map<String, Object>> results = finishOrder != null ? finishOrder : simulateResults(inscriptions);

            for (Map<String, Object> r : results) {
                Caballo caballo = caballoRepository.getReferenceById((Long) r.get("caballo_id"));
                ResultadoCarrera resultado = ResultadoCarrera.builder()
                        .carrera(race)
                        .caballo(caballo)
                        .posicionFinal((Integer) r.get("posicion"))
                        .tiempoFinal(new BigDecimal(r.get("tiempo").toString()))
                        .build();
                resultadoRepository.save(resultado);
            }

            if (!race.getTieneInteraccionHumana()) {
                carreraRepository.delete(race);
                log.info("[Lifecycle] Carrera #{} eliminada (solo bots)", raceId);
                return;
            }

            betSettlementService.settleRace(raceId, results);

            race.setEstado("finalizada");
            race.setFechaFinReal(LocalDateTime.now());
            carreraRepository.save(race);
            log.info("[Lifecycle] Carrera #{} finalizada", raceId);
        } catch (Exception e) {
            log.error("[Lifecycle] Error liquidando carrera #{}: {}", raceId, e.getMessage());
        } finally {
            settlingRaces.remove(raceId);
            processedRaceIds.remove(raceId);
        }
    }

    public List<Map<String, Object>> simulateResults(List<Inscripcion> inscriptions) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Inscripcion insc : inscriptions) {
            int velocidad = insc.getCaballo().getVelocidad() != null ? insc.getCaballo().getVelocidad() : 50;
            int resistencia = insc.getCaballo().getResistencia() != null ? insc.getCaballo().getResistencia() : 50;
            int corazon = insc.getCaballo().getCorazon() != null ? insc.getCaballo().getCorazon() : 50;
            double score = (velocidad * 0.5 + resistencia * 0.3 + corazon * 0.2) + Math.random() * 30;

            Map<String, Object> r = new HashMap<>();
            r.put("caballo_id", insc.getCaballo().getId());
            r.put("score", score);
            results.add(r);
        }

        results.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));

        int baseTime = 24;
        int timeSpread = 6;
        for (int idx = 0; idx < results.size(); idx++) {
            results.get(idx).put("posicion", idx + 1);
            double tiempo = baseTime + ((double) idx * timeSpread / results.size()) + Math.random() * 1.5;
            results.get(idx).put("tiempo", BigDecimal.valueOf(tiempo).setScale(2, java.math.RoundingMode.HALF_UP));
        }

        return results;
    }
}