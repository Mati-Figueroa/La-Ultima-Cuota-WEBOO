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
    public static final int TRACK_LENGTH = 1000;
    public static final int FINISH_PX = TRACK_WIDTH - HORSE_WIDTH;
    public static final double SPEED_SCALE = 0.65;

    private final ConcurrentHashMap<Long, RaceState> activeRaces = new ConcurrentHashMap<>();

    public void startSimulation(Long raceId) {
        List<Inscripcion> inscripciones = inscripcionRepository.findByCarreraIdWithCaballo(raceId);
        if (inscripciones.isEmpty()) return;

        List<SimHorse> horses = new ArrayList<>();
        Map<Long, Double> positions = new HashMap<>();
        for (Inscripcion insc : inscripciones) {
            Caballo c = insc.getCaballo();
            SimHorse h = new SimHorse(
                    c.getId(),
                    c.getVelocidad() != null ? c.getVelocidad() : 50,
                    c.getResistencia() != null ? c.getResistencia() : 50,
                    c.getCorazon() != null ? c.getCorazon() : 50
            );
            horses.add(h);
            positions.put(c.getId(), 0.0);
        }

        RaceState state = new RaceState(horses, positions, System.currentTimeMillis(), true);
        activeRaces.put(raceId, state);
        log.info("[Simulation] Carrera #{} simulación iniciada", raceId);
    }

    public TickResult tickSimulation(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        if (state == null || !state.running) return null;

        Random rand = new Random();
        double elapsed = (System.currentTimeMillis() - state.startTime) / 1000.0;
        List<SimHorse> finishedThisTick = new ArrayList<>();

        for (SimHorse h : state.horses) {
            if (h.finished) continue;

            double rngMove = rand.nextDouble() * 60 + 40;
            double speedBonus = rngMove * (h.velocidad / 500.0);

            double boostChance = 0.05 + (h.corazon * 0.001);
            double boostMult = rand.nextDouble() < boostChance ? 1.5 : 1.0;

            double fatigueMult = 1.0;
            if (h.pos > 700) {
                double tireChance = 1.0 - (h.resistencia / 120.0);
                if (rand.nextDouble() < tireChance) fatigueMult = 0.7;
            }

            double step = (rngMove + speedBonus) * boostMult * fatigueMult * SPEED_SCALE;
            double rawPos = h.pos + step;

            if (rawPos >= TRACK_LENGTH) {
                h.pos = TRACK_LENGTH;
                h.finished = true;
                double overshoot = rawPos - TRACK_LENGTH;
                double timeCorrection = step > 0 ? (overshoot / step) : 0;
                h.exactFinishTime = elapsed - timeCorrection;
                h.finishTime = Math.round(h.exactFinishTime * 100.0) / 100.0;
                finishedThisTick.add(h);
            } else {
                h.pos = rawPos;
            }
        }

        if (!finishedThisTick.isEmpty()) {
            finishedThisTick.sort(Comparator.comparingDouble(h -> h.exactFinishTime));
            state.finishOrder.addAll(finishedThisTick);
        }

        state.horses.forEach(h ->
                state.positions.put(h.caballoId,
                        (double) Math.round((h.pos / TRACK_LENGTH) * FINISH_PX)));

        Map<Long, Double> snapshot = new HashMap<>(state.positions);

        List<SimHorse> sortedLive = new ArrayList<>(state.horses);
        sortedLive.sort((a, b) -> {
            if (a.finished && b.finished) {
                return Double.compare(a.exactFinishTime, b.exactFinishTime);
            }
            if (a.finished) return -1;
            if (b.finished) return 1;
            return Double.compare(b.pos, a.pos);
        });

        Map<Long, Integer> rankings = new HashMap<>();
        for (int i = 0; i < sortedLive.size(); i++) {
            rankings.put(sortedLive.get(i).caballoId, i + 1);
        }

        if (state.finishOrder.size() == state.horses.size()) {
            state.running = false;
            activeRaces.remove(raceId);
            log.info("[Simulation] Carrera #{} terminada en {}s",
                    raceId, Math.round(System.currentTimeMillis() - state.startTime) / 1000);

            List<Map<String, Object>> results = new ArrayList<>();
            for (int idx = 0; idx < state.finishOrder.size(); idx++) {
                SimHorse h = state.finishOrder.get(idx);
                Map<String, Object> r = new HashMap<>();
                r.put("caballo_id", h.caballoId);
                r.put("posicion", idx + 1);
                r.put("tiempo", new BigDecimal(String.format(java.util.Locale.US, "%.2f", h.finishTime)));
                results.add(r);
            }
            return new TickResult(snapshot, rankings, (long) elapsed, results);
        }

        return new TickResult(snapshot, rankings, (long) elapsed, null);
    }

    public Map<Long, Double> getPositions(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        return state != null ? new HashMap<>(state.positions) : null;
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
        public final List<SimHorse> horses;
        public final Map<Long, Double> positions;
        public final List<SimHorse> finishOrder = new ArrayList<>();
        public final long startTime;
        public volatile boolean running;

        public RaceState(List<SimHorse> horses, Map<Long, Double> positions, long startTime, boolean running) {
            this.horses = horses;
            this.positions = positions;
            this.startTime = startTime;
            this.running = running;
        }
    }

    public static class SimHorse {
        public final Long caballoId;
        public final int velocidad;
        public final int resistencia;
        public final int corazon;
        public double pos;
        public boolean finished;
        public double exactFinishTime;
        public double finishTime;

        public SimHorse(Long caballoId, int velocidad, int resistencia, int corazon) {
            this.caballoId = caballoId;
            this.velocidad = velocidad;
            this.resistencia = resistencia;
            this.corazon = corazon;
        }
    }

    public static class TickResult {
        public final Map<Long, Double> positions;
        public final Map<Long, Integer> rankings;
        public final long elapsed;
        public final List<Map<String, Object>> results;

        public TickResult(Map<Long, Double> positions, Map<Long, Integer> rankings, long elapsed, List<Map<String, Object>> results) {
            this.positions = positions;
            this.rankings = rankings;
            this.elapsed = elapsed;
            this.results = results;
        }
    }
}