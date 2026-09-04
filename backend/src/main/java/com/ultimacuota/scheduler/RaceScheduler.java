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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class RaceScheduler {

    private final CarreraRepository carreraRepository;
    private final InscripcionRepository inscripcionRepository;
    private final CaballoRepository caballoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApuestaRepository apuestaRepository;
    private final TransaccionSaldoRepository transaccionRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final ResultadoCarreraRepository resultadoRepository;
    private final RaceSimulationService simulationService;
    private final BetSettlementService betSettlementService;

    @PersistenceContext
    private EntityManager entityManager;

    private SocketIOServer socketIOServer;
    private final Set<Long> processedRaceIds = ConcurrentHashMap.newKeySet();
    private boolean initialized = false;

    public void setSocketIOServer(SocketIOServer server) {
        this.socketIOServer = server;
    }

    @Scheduled(fixedRate = 1000, initialDelay = 5000)
    public void tickSimulations() {
        for (Long raceId : processedRaceIds) {
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
    public void manageRaces() {
        if (!initialized) {
            initializeScheduler();
            initialized = true;
        }
        transitionRaces();

        LocalDateTime now = LocalDateTime.now();
        int minute = now.getMinute();
        if (minute % 10 == 0) {
            long count = carreraRepository.countProgrammed();
            if (count < 4) {
                int toCreate = (int) (4 - count);
                for (int i = 0; i < toCreate; i++) {
                    createRaceWithBots(i * 10);
                }
            }
        }
    }

    private void initializeScheduler() {
        try {
            carreraRepository.deleteAllBotOnlyProgrammedRaces();
            long existing = carreraRepository.countProgrammed() + entityManager
                    .createQuery("SELECT COUNT(c) FROM Carrera c WHERE c.estado = 'en_curso'", Long.class)
                    .getSingleResult();
            int need = (int) Math.max(0, 4 - existing);
            for (int i = 0; i < need; i++) {
                createRaceWithBots(i * 10);
            }
            log.info("[Scheduler] Iniciado — carreras cada 10 min en punto, mínimo 4 programadas");
        } catch (Exception e) {
            log.error("[Scheduler] Error al iniciar: {}", e.getMessage());
        }
    }

    @Transactional
    public Carrera createRaceWithBots(int offsetMinutes) {
        try {
            LocalDateTime now = LocalDateTime.now().plusMinutes(offsetMinutes);
            int mins = now.getMinute();
            int alignedSlot = (mins / 10) * 10 + 10;
            LocalDateTime startIn;
            if (alignedSlot >= 60) {
                startIn = now.plusHours(1).withMinute(alignedSlot - 60).withSecond(0).withNano(0);
            } else {
                startIn = now.withMinute(alignedSlot).withSecond(0).withNano(0);
            }

            Carrera race = Carrera.builder()
                    .nombre(null)
                    .estado("programada")
                    .fechaProgramada(startIn)
                    .cupoMaximo(12)
                    .tieneInteraccionHumana(false)
                    .build();
            race = carreraRepository.save(race);

            int numBots = new Random().nextInt(4) + 6;
            List<Caballo> botHorses = caballoRepository.findRandomBotHorses();
            if (botHorses.size() > numBots) {
                botHorses = botHorses.subList(0, numBots);
            }

            int carril = 1;
            for (Caballo bot : botHorses) {
                Inscripcion inscripcion = Inscripcion.builder()
                        .carrera(race)
                        .caballo(bot)
                        .usuario(null)
                        .numeroCarril(carril++)
                        .build();
                inscripcionRepository.save(inscripcion);
            }

            log.info("[Scheduler] Carrera #{} creada con {} bots para {}",
                    race.getId(), botHorses.size(), startIn);

            List<Inscripcion> allInscriptions = inscripcionRepository.findByCarreraIdWithCaballo(race.getId());
            createBotBets(race.getId(), allInscriptions);

            return race;
        } catch (Exception e) {
            log.error("[Scheduler] Error creando carrera: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public void createBotBets(Long raceId, List<Inscripcion> inscriptions) {
        try {
            List<Inscripcion> botHorses = inscriptions.stream()
                    .filter(i -> i.getCaballo().getEsBot()).toList();

            BigDecimal totalPool = BigDecimal.ZERO;
            List<Apuesta> allBotBets = new ArrayList<>();

            for (Inscripcion bot : botHorses) {
                List<Map<String, Object>> botBetData = simulationService.generateBotBets(raceId, inscriptions);
                for (Map<String, Object> bd : botBetData) {
                    Caballo caballo = caballoRepository.getReferenceById((Long) bd.get("caballo_id"));
                    Apuesta bet = Apuesta.builder()
                            .usuario(null)
                            .carrera(carreraRepository.getReferenceById(raceId))
                            .caballo(caballo)
                            .monto(new BigDecimal(bd.get("monto").toString()))
                            .cuota(BigDecimal.ONE)
                            .estado("pendiente")
                            .build();
                    allBotBets.add(bet);
                    totalPool = totalPool.add(bet.getMonto());
                }
            }

            for (Apuesta bet : allBotBets) {
                apuestaRepository.save(bet);
            }

            log.info("[BotSim] Carrera #{}: {} apuestas de bots creadas, pool total: ${}",
                    raceId, allBotBets.size(), totalPool);
        } catch (Exception e) {
            log.error("[BotSim] Error creando apuestas bots carrera #{}: {}", raceId, e.getMessage());
        }
    }

    @Transactional
    public void fillWithBots(Long raceId) {
        try {
            List<Inscripcion> existing = inscripcionRepository.findByCarreraIdWithCaballo(raceId);
            List<Long> existingIds = existing.stream().map(i -> i.getCaballo().getId()).toList();
            int emptySlots = 12 - existing.size();
            int minBots = (int) Math.ceil(emptySlots / 2.0);
            int maxBots = (int) Math.floor(emptySlots * 3.0 / 4);
            int numBots = new Random().nextInt(Math.max(maxBots - minBots + 1, 1)) + minBots;

            List<Caballo> botHorses = caballoRepository.findRandomBotHorses();
            botHorses = botHorses.stream()
                    .filter(b -> !existingIds.contains(b.getId()))
                    .limit(numBots).toList();

            int carril = existing.size() + 1;
            for (Caballo bot : botHorses) {
                Inscripcion inscripcion = Inscripcion.builder()
                        .carrera(carreraRepository.getReferenceById(raceId))
                        .caballo(bot)
                        .usuario(null)
                        .numeroCarril(carril++)
                        .build();
                inscripcionRepository.save(inscripcion);
            }

            log.info("[Scheduler] Carrera #{} rellenada con {} bots", raceId, botHorses.size());

            List<Inscripcion> allInscriptions = inscripcionRepository.findByCarreraIdWithCaballo(raceId);
            createBotBets(raceId, allInscriptions);
        } catch (Exception e) {
            log.error("[Scheduler] Error rellenando carrera #{}: {}", raceId, e.getMessage());
        }
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
                    fillWithBots(race.getId());
                }

                simulationService.startSimulation(race.getId());
                processedRaceIds.add(race.getId());

                if (socketIOServer != null) {
                    socketIOServer.getBroadcastOperations().sendEvent("race_started",
                            Map.of("carrera_id", race.getId()));
                }
                log.info("[Scheduler] Carrera #{} iniciada", race.getId());
            }

            List<Carrera> toFinish = carreraRepository.findRunningWithStartTime();
            BigDecimal raceDuration = configuracionRepository.findByClave("race_duration_seconds")
                    .map(c -> new BigDecimal(c.getValor()))
                    .orElse(BigDecimal.valueOf(30));

            for (Carrera race : toFinish) {
                long elapsed = java.time.Duration.between(race.getFechaInicioReal(), now).getSeconds();
                if (elapsed < raceDuration.longValue()) continue;

                if (resultadoRepository.existsByCarreraId(race.getId())) {
                    race.setEstado("finalizada");
                    race.setFechaFinReal(now);
                    carreraRepository.save(race);
                    processedRaceIds.remove(race.getId());
                    continue;
                }

                List<Inscripcion> inscriptions = inscripcionRepository.findByCarreraIdWithCaballo(race.getId());
                if (!inscriptions.isEmpty()) {
                    List<Map<String, Object>> results = simulateResults(inscriptions);

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

                    if (race.getTieneInteraccionHumana()) {
                        betSettlementService.settleRace(race.getId(), results);
                        race.setEstado("finalizada");
                        race.setFechaFinReal(now);
                        carreraRepository.save(race);
                        log.info("[Scheduler] Carrera #{} finalizada", race.getId());
                    } else {
                        carreraRepository.delete(race);
                        log.info("[Scheduler] Carrera #{} eliminada (solo bots)", race.getId());
                    }
                } else {
                    carreraRepository.delete(race);
                    log.info("[Scheduler] Carrera #{} eliminada (sin inscripciones)", race.getId());
                }
                processedRaceIds.remove(race.getId());
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error en transiciones: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> simulateResults(List<Inscripcion> inscriptions) {
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
