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
    private final JineteRepository jineteRepository;

    public static final int TRACK_WIDTH = 800;
    public static final int HORSE_WIDTH = 40;
    public static final int TRACK_LENGTH = 1000;
    public static final int FINISH_PX = TRACK_WIDTH - HORSE_WIDTH;
    public static final double SPEED_SCALE = 0.65;

    private final ConcurrentHashMap<Long, RaceState> activeRaces = new ConcurrentHashMap<>();
    private final Random random = new Random();

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
            h.nombre = c.getNombre();
            h.felicidad = c.getFelicidad() != null ? c.getFelicidad() : 70;
            h.lesionInicial = c.getLesion();
            if (!Boolean.TRUE.equals(c.getEsBot())) {
                jineteRepository.findValidoPorCaballo(c.getId()).ifPresent(j -> {
                    h.jineteHabilidad = j.getHabilidad() != null ? j.getHabilidad() : 0;
                    h.jineteEspecialidad = j.getEspecialidad();
                });
            }
            horses.add(h);
            positions.put(c.getId(), 0.0);
        }

        RaceState state = new RaceState(horses, positions, System.currentTimeMillis(), true);
        state.plan.addAll(RaceEventEngine.planificar(random));
        activeRaces.put(raceId, state);
        log.info("[Simulation] Carrera #{} simulación iniciada con {} evento(s) planificado(s)", raceId, state.plan.size());
    }

    public TickResult tickSimulation(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        if (state == null || !state.running) return null;

        double elapsed = (System.currentTimeMillis() - state.startTime) / 1000.0;
        TickResult result = RaceEventEngine.tick(state, random, elapsed);

        if (result.results != null) {
            activeRaces.remove(raceId);
            log.info("[Simulation] Carrera #{} terminada en {}s con {} evento(s)",
                    raceId, Math.round(elapsed), state.eventosOcurridos.size());
        }
        return result;
    }

    public Map<Long, Double> getPositions(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        return state != null ? new HashMap<>(state.positions) : null;
    }

    public List<Map<String, Object>> getEventos(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        return state != null ? new ArrayList<>(state.eventosOcurridos) : List.of();
    }

    public Map<Long, String> getEstados(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        if (state == null) return Map.of();
        Map<Long, String> estados = new HashMap<>();
        for (SimHorse h : state.horses) {
            if (h.estado != null) estados.put(h.caballoId, h.estado);
        }
        return estados;
    }

    public String getClima(Long raceId) {
        RaceState state = activeRaces.get(raceId);
        return state != null ? state.clima : null;
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

    public static class EventoPlanificado {
        public final double umbral;
        public final String tipo;

        public EventoPlanificado(double umbral, String tipo) {
            this.umbral = umbral;
            this.tipo = tipo;
        }
    }

    public static class RaceState {
        public final List<SimHorse> horses;
        public final Map<Long, Double> positions;
        public final List<SimHorse> finishOrder = new ArrayList<>();
        public final List<EventoPlanificado> plan = new ArrayList<>();
        public final List<Map<String, Object>> eventosOcurridos = Collections.synchronizedList(new ArrayList<>());
        public final long startTime;
        public volatile boolean running;
        public volatile String clima;

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
        public String nombre;
        public int felicidad = 70;
        public String lesionInicial;
        public int jineteHabilidad;
        public String jineteEspecialidad;

        public double pos;
        public boolean finished;
        public boolean dnf;
        public double exactFinishTime;
        public double finishTime;

        public int stunTicks;
        public boolean envenenado;
        public double venenoMult = 1.0;
        /** Estado visible: aturdido, envenenado, abducido, colapso. */
        public String estado;

        public String lesionPendiente;
        public int deltaFelicidad;
        public int fatigaExtra;

        public SimHorse(Long caballoId, int velocidad, int resistencia, int corazon) {
            this.caballoId = caballoId;
            this.velocidad = velocidad;
            this.resistencia = resistencia;
            this.corazon = corazon;
            this.nombre = "Caballo " + caballoId;
        }
    }

    public static class TickResult {
        public final Map<Long, Double> positions;
        public final Map<Long, Integer> rankings;
        public final long elapsed;
        public final List<Map<String, Object>> results;
        public final List<Map<String, Object>> eventos;
        public final Map<Long, String> estados;
        public final String clima;

        public TickResult(Map<Long, Double> positions, Map<Long, Integer> rankings, long elapsed,
                          List<Map<String, Object>> results, List<Map<String, Object>> eventos,
                          Map<Long, String> estados, String clima) {
            this.positions = positions;
            this.rankings = rankings;
            this.elapsed = elapsed;
            this.results = results;
            this.eventos = eventos;
            this.estados = estados;
            this.clima = clima;
        }
    }
}
