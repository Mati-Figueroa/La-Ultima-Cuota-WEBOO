package com.ultimacuota.scheduler;

import com.ultimacuota.repositories.CarreraRepository;
import com.ultimacuota.services.RaceSimulationService;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class RaceScheduler {

    private final CarreraRepository carreraRepository;
    private final RaceSimulationService simulationService;
    private final RaceCreator raceCreator;
    private final RaceLifecycleManager raceLifecycleManager;

    private SocketIOServer socketIOServer;

    public void setSocketIOServer(SocketIOServer server) {
        this.socketIOServer = server;
        raceLifecycleManager.setSocketIOServer(server);
    }

    @Scheduled(fixedRate = 1000, initialDelay = 5000)
    public void tickSimulations() {
        for (Long raceId : raceLifecycleManager.getProcessedRaceIds()) {
            if (simulationService.isRunning(raceId)) {
                simulationService.tickSimulation(raceId);
                Map<Long, Double> positions = simulationService.getPositions(raceId);
                if (positions != null && socketIOServer != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("carrera_id", raceId);
                    data.put("positions", positions);
                    data.put("elapsed", simulationService.getElapsed(raceId));
                    socketIOServer.getBroadcastOperations().sendEvent("race_positions", data);
                }
            }
        }
    }

    @Scheduled(fixedRate = 60000, initialDelay = 2000)
    @Transactional
    public void manageRaces() {
        if (!initialized) {
            initializeScheduler();
            initialized = true;
        }
        raceLifecycleManager.transitionRaces();

        LocalDateTime now = LocalDateTime.now();
        int minute = now.getMinute();
        if (minute % 10 == 0) {
            long count = carreraRepository.countProgrammed();
            if (count < 4) {
                int toCreate = (int) (4 - count);
                for (int i = 0; i < toCreate; i++) {
                    raceCreator.createRaceWithBots(i * 10);
                }
            }
        }
    }

    private boolean initialized = false;

    private void initializeScheduler() {
        try {
            carreraRepository.deleteAllBotOnlyProgrammedRaces();
            long existing = carreraRepository.countProgrammed();
            int need = (int) Math.max(0, 4 - existing);
            for (int i = 0; i < need; i++) {
                raceCreator.createRaceWithBots(i * 10);
            }
            log.info("[Scheduler] Iniciado — carreras cada 10 min en punto, mínimo 4 programadas");
        } catch (Exception e) {
            log.error("[Scheduler] Error al iniciar: {}", e.getMessage());
        }
    }
}
