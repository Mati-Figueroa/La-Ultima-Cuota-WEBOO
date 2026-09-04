package com.ultimacuota.services;

import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceSimulationService {

    private final CaballoRepository caballoRepository;
    private final InscripcionRepository inscripcionRepository;

    public static final int TRACK_WIDTH = 800;
    public static final int HORSE_WIDTH = 40;

    private final ConcurrentHashMap<Long, RaceState> activeRaces = new ConcurrentHashMap<>();

    public void startSimulation(Long raceId) {
        List<Inscripcion> inscripciones = inscripcionRepository.findByCarreraIdWithCaballo(raceId);
        if (inscripciones.isEmpty()) return;

        Map<Long, Double> positions = new HashMap<>();
        inscripciones.forEach(i -> positions.put(i.getCaballo().getId(), 0.0));

        long startTime = System.currentTimeMillis();
        RaceState state = new RaceState(positions, startTime, true);
        activeRaces.put(raceId, state);

        log.info("[Simulation] Carrera #{} simulación iniciada", raceId);
    }

    public void tickSimulation(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        if (state == null || !state.running) return;

        Random rand = new Random();
        state.positions.forEach((horseId, pos) -> {
            if (pos >= TRACK_WIDTH - HORSE_WIDTH) return;
            double speed = (rand.nextDouble() * 10 + 3) * 2;
            state.positions.put(horseId, Math.min(pos + speed, TRACK_WIDTH - HORSE_WIDTH));
        });

        boolean allFinished = state.positions.values().stream()
                .allMatch(p -> p >= TRACK_WIDTH - HORSE_WIDTH);

        if (allFinished) {
            state.running = false;
            activeRaces.remove(raceId);
            log.info("[Simulation] Carrera #{} terminada en {}s", raceId,
                    (System.currentTimeMillis() - state.startTime) / 1000);
        }
    }

    public Map<Long, Double> getPositions(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        return state != null ? state.positions : null;
    }

    public boolean isRunning(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        return state != null && state.running;
    }

    public long getElapsed(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        return state != null ? (System.currentTimeMillis() - state.startTime) / 1000 : 0;
    }

    public List<Map<String, Object>> generateBotBets(Long raceId, List<Inscripcion> inscriptions) {
        List<Inscripcion> botHorses = inscriptions.stream()
                .filter(i -> i.getCaballo().getEsBot()).toList();
        List<Inscripcion> humanHorses = inscriptions.stream()
                .filter(i -> !i.getCaballo().getEsBot()).toList();

        List<Map<String, Object>> allBotBets = new ArrayList<>();
        BigDecimal totalPool = BigDecimal.ZERO;

        for (Inscripcion bot : botHorses) {
            List<Map<String, Object>> botBets = generateBotBetsForHorse(bot, raceId, totalPool, inscriptions);
            allBotBets.addAll(botBets);
            totalPool = totalPool.add(botBets.stream()
                    .map(b -> new BigDecimal(b.get("monto").toString()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return allBotBets;
    }

    private double calculateWinrate(Caballo horse) {
        int v = horse.getVelocidad() != null ? horse.getVelocidad() : 50;
        int r = horse.getResistencia() != null ? horse.getResistencia() : 50;
        int c = horse.getCorazon() != null ? horse.getCorazon() : 50;
        return (v * 0.5 + r * 0.3 + c * 0.2) / 100.0;
    }

    private List<Map<String, Object>> generateBotBetsForHorse(
            Inscripcion insc, Long raceId, BigDecimal totalPool, List<Inscripcion> allHorses) {

        double winrate = calculateWinrate(insc.getCaballo());
        List<Map<String, Object>> bets = new ArrayList<>();
        Random rand = new Random();
        int numBets = rand.nextInt(4) + 1;

        for (int i = 0; i < numBets; i++) {
            BigDecimal botPoolShare = totalPool.compareTo(BigDecimal.ZERO) > 0
                    ? totalPool.multiply(BigDecimal.valueOf(0.05 + rand.nextDouble() * 0.2))
                    : BigDecimal.valueOf(100);

            int minBet = Math.max(50, botPoolShare.multiply(BigDecimal.valueOf(0.1)).intValue());
            int maxBet = botPoolShare.multiply(BigDecimal.valueOf(0.6)).intValue();
            if (maxBet < minBet) maxBet = minBet;
            int monto = rand.nextInt(maxBet - minBet + 1) + minBet;

            boolean preferStronger = rand.nextDouble() < 0.6;
            if (preferStronger && winrate < 0.4) continue;

            Map<String, Object> bet = new HashMap<>();
            bet.put("carrera_id", raceId);
            bet.put("caballo_id", insc.getCaballo().getId());
            bet.put("monto", monto);
            bet.put("cuota", BigDecimal.ONE);
            bet.put("estado", "pendiente");
            bets.add(bet);
        }
        return bets;
    }

    public static class RaceState {
        public final Map<Long, Double> positions;
        public final long startTime;
        public volatile boolean running;

        public RaceState(Map<Long, Double> positions, long startTime, boolean running) {
            this.positions = positions;
            this.startTime = startTime;
            this.running = running;
        }
    }
}
