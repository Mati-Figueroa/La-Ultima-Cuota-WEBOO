package com.ultimacuota.scheduler;

import com.ultimacuota.models.EventoCarrera;
import com.ultimacuota.repositories.CaballoRepository;
import com.ultimacuota.repositories.CarreraRepository;
import com.ultimacuota.repositories.EventoCarreraRepository;
import com.ultimacuota.services.RaceSimulationService;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class RaceScheduler {

    private final CarreraRepository carreraRepository;
    private final RaceSimulationService simulationService;
    private final RaceCreator raceCreator;
    private final RaceLifecycleManager raceLifecycleManager;
    private final EventoCarreraRepository eventoCarreraRepository;
    private final CaballoRepository caballoRepository;

    private SocketIOServer socketIOServer;

    public void setSocketIOServer(SocketIOServer server) {
        this.socketIOServer = server;
        raceLifecycleManager.setSocketIOServer(server);
    }

    private final Set<Long> notifiedStartingSoon = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRate = 1000, initialDelay = 2000)
    public void tickSimulations() {
        checkStartingSoonRaces();

        for (Long raceId : raceLifecycleManager.getProcessedRaceIds()) {
            if (!simulationService.isRunning(raceId)) continue;

            RaceSimulationService.TickResult tick = simulationService.tickSimulation(raceId);
            if (tick == null) continue;

            if (tick.eventos != null) {
                for (Map<String, Object> evento : tick.eventos) {
                    registrarEvento(raceId, evento);
                    if (socketIOServer != null) {
                        Map<String, Object> evData = new HashMap<>(evento);
                        evData.put("carrera_id", raceId);
                        // Solo broadcast: también llega a la sala y así nadie lo recibe dos veces.
                        socketIOServer.getBroadcastOperations().sendEvent("race_event", evData);
                    }
                }
            }

            if (tick.positions != null && socketIOServer != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("carrera_id", raceId);
                data.put("positions", tick.positions);
                data.put("rankings", tick.rankings);
                data.put("elapsed", tick.elapsed);
                data.put("estados", tick.estados);
                data.put("clima", tick.clima);
                socketIOServer.getRoomOperations("race_" + raceId).sendEvent("race_positions", data);
                socketIOServer.getBroadcastOperations().sendEvent("race_positions", data);
            }

            if (tick.results != null) {
                log.info("[Scheduler] Carrera #{} terminada, liquidando", raceId);
                raceLifecycleManager.settleRace(raceId, tick.results);
                if (socketIOServer != null) {
                    Map<String, Object> finishData = new HashMap<>();
                    finishData.put("carrera_id", raceId);
                    finishData.put("results", tick.results);
                    socketIOServer.getRoomOperations("race_" + raceId).sendEvent("race_finished", finishData);
                    socketIOServer.getBroadcastOperations().sendEvent("race_finished", finishData);
                }
            }
        }
    }

    private void registrarEvento(Long raceId, Map<String, Object> evento) {
        try {
            Object caballoId = evento.get("caballo_id");
            EventoCarrera registro = EventoCarrera.builder()
                    .carrera(carreraRepository.getReferenceById(raceId))
                    .tipo((String) evento.get("tipo"))
                    .caballo(caballoId instanceof Long id ? caballoRepository.getReferenceById(id) : null)
                    .segundo(evento.get("segundo") instanceof Number n ? n.intValue() : 0)
                    .descripcion((String) evento.get("descripcion"))
                    .build();
            eventoCarreraRepository.save(registro);
            log.info("[Eventos] Carrera #{}: {}", raceId, evento.get("descripcion"));
        } catch (Exception e) {
            log.error("[Eventos] No se pudo registrar el evento de la carrera #{}: {}", raceId, e.getMessage());
        }
    }

    private void checkStartingSoonRaces() {
        if (socketIOServer == null) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            List<com.ultimacuota.models.Carrera> programmed = carreraRepository.findAllByEstado("programada");
            for (com.ultimacuota.models.Carrera race : programmed) {
                if (notifiedStartingSoon.contains(race.getId())) continue;
                long secondsUntil = java.time.Duration.between(now, race.getFechaProgramada()).getSeconds();
                if (secondsUntil >= 0 && secondsUntil <= 10) {
                    notifiedStartingSoon.add(race.getId());
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("carrera_id", race.getId());
                    eventData.put("seconds_left", Math.max(1, secondsUntil));
                    socketIOServer.getBroadcastOperations().sendEvent("race_starting_soon", eventData);
                    log.info("[Scheduler] Carrera #{} comenzará en {} segundos! Evento emitido.", race.getId(), secondsUntil);
                }
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error verificando inicio cercano: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 30000, initialDelay = 2000)
    @Transactional
    public void manageRaces() {
        if (!initialized) {
            initializeScheduler();
            initialized = true;
        }
        raceLifecycleManager.transitionRaces();

        LocalDateTime now = LocalDateTime.now();
        List<com.ultimacuota.models.Carrera> programmed = carreraRepository.findAllByEstado("programada");
        if (programmed.size() < 4) {
            LocalDateTime lastTime = programmed.stream()
                    .map(com.ultimacuota.models.Carrera::getFechaProgramada)
                    .max(LocalDateTime::compareTo)
                    .orElse(now);

            int toCreate = 4 - programmed.size();
            for (int i = 0; i < toCreate; i++) {
                // lastTime avanza en cada vuelta, así que siempre se pide el siguiente horario (índice 0).
                LocalDateTime nextSlot = getNextClockSlot(lastTime, 0);
                raceCreator.createRaceAt(nextSlot);
                lastTime = nextSlot;
            }
        }
    }

    private boolean initialized = false;

    private void initializeScheduler() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<com.ultimacuota.models.Carrera> oldProgrammed = carreraRepository.findAllByEstado("programada");
            for (com.ultimacuota.models.Carrera race : oldProgrammed) {
                carreraRepository.delete(race);
            }
            log.info("[Scheduler] Limpiadas {} carreras programadas obsoletas", oldProgrammed.size());

            for (int i = 0; i < 4; i++) {
                LocalDateTime slot = getNextClockSlot(now, i);
                raceCreator.createRaceAt(slot);
            }
            log.info("[Scheduler] Iniciado — Carreras programadas a los minutos :00, :10, :20, :30, :40, :50");
        } catch (Exception e) {
            log.error("[Scheduler] Error al iniciar: {}", e.getMessage());
        }
    }

    public static LocalDateTime getNextClockSlot(LocalDateTime from, int index) {
        int minute = from.getMinute();
        int nextTen = ((minute / 10) + 1) * 10;
        LocalDateTime firstSlot = from.withMinute(0).withSecond(0).withNano(0).plusMinutes(nextTen);
        if (java.time.Duration.between(from, firstSlot).getSeconds() < 90) {
            firstSlot = firstSlot.plusMinutes(10);
        }
        return firstSlot.plusMinutes((long) index * 10);
    }
}
