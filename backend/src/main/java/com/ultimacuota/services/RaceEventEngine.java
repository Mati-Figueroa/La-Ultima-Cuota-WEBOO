package com.ultimacuota.services;

import com.ultimacuota.services.RaceSimulationService.RaceState;
import com.ultimacuota.services.RaceSimulationService.SimHorse;
import com.ultimacuota.services.RaceSimulationService.TickResult;

import java.math.BigDecimal;
import java.util.*;

/**
 * Lógica pura de la simulación de carreras y de sus eventos aleatorios.
 * No toca la base de datos; recibe el {@link Random} para que los tests sean deterministas.
 */
public final class RaceEventEngine {

    private RaceEventEngine() {}

    public static final int TRACK_LENGTH = RaceSimulationService.TRACK_LENGTH;
    public static final int FINISH_PX = RaceSimulationService.FINISH_PX;
    public static final double SPEED_SCALE = RaceSimulationService.SPEED_SCALE;

    public static final String LLUVIA = "lluvia";
    public static final String BARRO = "barro";
    public static final String SEQUIA = "sequia";
    public static final String RAYO = "rayo";
    public static final String BOMBA = "bomba";
    public static final String ENVENENAMIENTO = "envenenamiento";
    public static final String ABDUCCION = "abduccion";
    public static final String MUERTE_SUBITA = "muerte_subita";

    public static final Set<String> CLIMAS = Set.of(LLUVIA, BARRO, SEQUIA);

    /** Peso relativo de cada evento al sortearlo. */
    private static final LinkedHashMap<String, Integer> PESOS = new LinkedHashMap<>();
    static {
        PESOS.put(LLUVIA, 3);
        PESOS.put(BARRO, 3);
        PESOS.put(SEQUIA, 3);
        PESOS.put(RAYO, 3);
        PESOS.put(BOMBA, 3);
        PESOS.put(ENVENENAMIENTO, 3);
        PESOS.put(ABDUCCION, 2);
        PESOS.put(MUERTE_SUBITA, 1);
    }

    public static final String LESION_LEVE = "leve";
    public static final String LESION_MODERADA = "moderada";
    public static final String LESION_GRAVE = "grave";

    public static int nivelLesion(String lesion) {
        if (lesion == null) return 0;
        return switch (lesion) {
            case LESION_LEVE -> 1;
            case LESION_MODERADA -> 2;
            case LESION_GRAVE -> 3;
            default -> 0;
        };
    }

    public static String peorLesion(String a, String b) {
        return nivelLesion(a) >= nivelLesion(b) ? a : b;
    }

    // ------------------------------------------------------------------
    // Planificación
    // ------------------------------------------------------------------

    /** Entre 1 y 3 eventos; el primero siempre ocurre y como máximo uno es de clima. */
    public static List<RaceSimulationService.EventoPlanificado> planificar(Random rand) {
        int cantidad = 1 + (rand.nextDouble() < 0.5 ? 1 : 0) + (rand.nextDouble() < 0.2 ? 1 : 0);
        List<RaceSimulationService.EventoPlanificado> plan = new ArrayList<>();
        boolean hayClima = false;
        for (int i = 0; i < cantidad; i++) {
            // El primero se dispara temprano (15–45 %) para garantizar que ocurra.
            double umbral = i == 0 ? 0.15 + rand.nextDouble() * 0.30 : 0.20 + rand.nextDouble() * 0.60;
            String tipo = sortearTipo(rand, hayClima);
            if (CLIMAS.contains(tipo)) hayClima = true;
            plan.add(new RaceSimulationService.EventoPlanificado(umbral, tipo));
        }
        plan.sort(Comparator.comparingDouble(e -> e.umbral));
        return plan;
    }

    static String sortearTipo(Random rand, boolean excluirClima) {
        int total = 0;
        for (Map.Entry<String, Integer> e : PESOS.entrySet()) {
            if (excluirClima && CLIMAS.contains(e.getKey())) continue;
            total += e.getValue();
        }
        int r = rand.nextInt(total);
        for (Map.Entry<String, Integer> e : PESOS.entrySet()) {
            if (excluirClima && CLIMAS.contains(e.getKey())) continue;
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        return RAYO;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static TickResult tick(RaceState state, Random rand, double elapsed) {
        List<SimHorse> finishedThisTick = new ArrayList<>();

        for (SimHorse h : state.horses) {
            if (h.finished || h.dnf) continue;

            if (h.stunTicks > 0) {
                h.stunTicks--;
                if (h.stunTicks == 0 && "aturdido".equals(h.estado)) {
                    h.estado = h.envenenado ? "envenenado" : null;
                }
                continue;
            }

            double rngMove = rand.nextDouble() * 60 + 40;
            double speedBonus = rngMove * (h.velocidad / 500.0);

            double boostChance = 0.05 + (h.corazon * 0.001);
            double boostMult = rand.nextDouble() < boostChance ? 1.5 : 1.0;

            double fatigueMult = 1.0;
            double inicioCansancio = SEQUIA.equals(state.clima) ? 500 : 700;
            if (h.pos > inicioCansancio) {
                double tireChance = 1.0 - (h.resistencia / 120.0);
                if (SEQUIA.equals(state.clima) && SEQUIA.equals(h.jineteEspecialidad)) tireChance /= 2;
                if (rand.nextDouble() < tireChance) fatigueMult = 0.7;
            }

            if (h.envenenado) {
                h.venenoMult = Math.max(0.45, h.venenoMult - 0.04);
            }

            double step = (rngMove + speedBonus) * boostMult * fatigueMult * SPEED_SCALE
                    * multiplicadorClima(state.clima, h)
                    * h.venenoMult
                    * multiplicadorFelicidad(h.felicidad)
                    * multiplicadorLesion(h.lesionInicial)
                    * (1.0 + h.jineteHabilidad / 1000.0);
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

        List<Map<String, Object>> eventos = dispararEventos(state, rand, elapsed);

        state.horses.forEach(h ->
                state.positions.put(h.caballoId, (double) Math.round((h.pos / TRACK_LENGTH) * FINISH_PX)));
        Map<Long, Double> snapshot = new HashMap<>(state.positions);

        List<SimHorse> sortedLive = new ArrayList<>(state.horses);
        sortedLive.sort((a, b) -> {
            if (a.dnf != b.dnf) return a.dnf ? 1 : -1;
            if (a.finished && b.finished) return Double.compare(a.exactFinishTime, b.exactFinishTime);
            if (a.finished) return -1;
            if (b.finished) return 1;
            return Double.compare(b.pos, a.pos);
        });
        Map<Long, Integer> rankings = new HashMap<>();
        for (int i = 0; i < sortedLive.size(); i++) {
            rankings.put(sortedLive.get(i).caballoId, i + 1);
        }

        Map<Long, String> estados = new HashMap<>();
        for (SimHorse h : state.horses) {
            if (h.estado != null) estados.put(h.caballoId, h.estado);
        }

        boolean todosTerminaron = state.horses.stream().allMatch(h -> h.finished || h.dnf);
        if (!todosTerminaron) {
            return new TickResult(snapshot, rankings, (long) elapsed, null, eventos, estados, state.clima);
        }

        state.running = false;
        return new TickResult(snapshot, rankings, (long) elapsed, construirResultados(state), eventos, estados, state.clima);
    }

    static List<Map<String, Object>> construirResultados(RaceState state) {
        List<SimHorse> orden = new ArrayList<>(state.finishOrder);
        List<SimHorse> abandonos = state.horses.stream().filter(h -> h.dnf)
                .sorted((a, b) -> Double.compare(b.pos, a.pos)).toList();
        orden.addAll(abandonos);

        List<Map<String, Object>> results = new ArrayList<>();
        for (int idx = 0; idx < orden.size(); idx++) {
            SimHorse h = orden.get(idx);
            Map<String, Object> r = new HashMap<>();
            r.put("caballo_id", h.caballoId);
            r.put("posicion", idx + 1);
            r.put("tiempo", h.dnf ? null
                    : new BigDecimal(String.format(Locale.US, "%.2f", h.finishTime)));
            r.put("abandono", h.dnf);
            r.put("motivo_abandono", h.dnf ? h.estado : null);
            r.put("lesion_pendiente", h.lesionPendiente);
            r.put("delta_felicidad", h.deltaFelicidad);
            r.put("fatiga_extra", h.fatigaExtra);
            results.add(r);
        }
        return results;
    }

    // ------------------------------------------------------------------
    // Multiplicadores
    // ------------------------------------------------------------------

    static double multiplicadorClima(String clima, SimHorse h) {
        if (clima == null) return 1.0;
        return switch (clima) {
            case LLUVIA -> Math.min(1.0, 0.85 + h.resistencia / 100.0 * 0.08
                    + (LLUVIA.equals(h.jineteEspecialidad) ? 0.07 : 0));
            case BARRO -> Math.min(1.0, 0.75 + h.corazon / 100.0 * 0.10
                    + (BARRO.equals(h.jineteEspecialidad) ? 0.08 : 0));
            default -> 1.0;
        };
    }

    static double multiplicadorFelicidad(int felicidad) {
        return 0.9 + Math.max(0, Math.min(100, felicidad)) / 500.0;
    }

    static double multiplicadorLesion(String lesion) {
        return switch (nivelLesion(lesion)) {
            case 1 -> 0.9;
            case 2 -> 0.75;
            case 3 -> 0.6;
            default -> 1.0;
        };
    }

    // ------------------------------------------------------------------
    // Eventos
    // ------------------------------------------------------------------

    static List<Map<String, Object>> dispararEventos(RaceState state, Random rand, double elapsed) {
        List<Map<String, Object>> disparados = new ArrayList<>();
        double lider = state.horses.stream().filter(h -> !h.dnf).mapToDouble(h -> h.pos).max().orElse(0);
        double progreso = lider / TRACK_LENGTH;

        Iterator<RaceSimulationService.EventoPlanificado> it = state.plan.iterator();
        while (it.hasNext()) {
            RaceSimulationService.EventoPlanificado ev = it.next();
            if (progreso < ev.umbral) break;
            it.remove();
            Map<String, Object> evento = aplicar(state, ev.tipo, rand, (int) Math.round(elapsed));
            if (evento != null) {
                disparados.add(evento);
                state.eventosOcurridos.add(evento);
            }
        }
        return disparados;
    }

    private static List<SimHorse> enCarrera(RaceState state) {
        return state.horses.stream().filter(h -> !h.finished && !h.dnf).toList();
    }

    /** Aplica un evento. Si no hay a quién aplicarlo, cae a un evento de clima (siempre aplicable). */
    static Map<String, Object> aplicar(RaceState state, String tipo, Random rand, int segundo) {
        List<SimHorse> corriendo = enCarrera(state);

        if (!CLIMAS.contains(tipo) && corriendo.isEmpty()) {
            tipo = state.clima == null ? LLUVIA : null;
            if (tipo == null) return null;
        }
        if (CLIMAS.contains(tipo) && state.clima != null) {
            // Ya hay clima: lo reemplazamos por un evento puntual si hay a quién.
            if (corriendo.isEmpty()) return null;
            tipo = RAYO;
        }
        // Un abandono nunca deja la carrera sin nadie corriendo.
        if ((ABDUCCION.equals(tipo) || MUERTE_SUBITA.equals(tipo)) && corriendo.size() < 2) {
            tipo = RAYO;
        }

        Map<String, Object> evento = new HashMap<>();
        evento.put("tipo", tipo);
        evento.put("segundo", segundo);
        List<Long> afectados = new ArrayList<>();
        String descripcion;

        switch (tipo) {
            case LLUVIA -> {
                state.clima = LLUVIA;
                descripcion = "¡Empieza a llover! La pista se vuelve resbaladiza y todos frenan.";
            }
            case BARRO -> {
                state.clima = BARRO;
                descripcion = "¡La pista se convierte en un barrial! Todos avanzan más lento.";
            }
            case SEQUIA -> {
                state.clima = SEQUIA;
                for (SimHorse h : state.horses) h.fatigaExtra += 10;
                descripcion = "¡Sequía extrema! El calor agota a los caballos antes de tiempo.";
            }
            case RAYO -> {
                SimHorse h = corriendo.get(rand.nextInt(corriendo.size()));
                h.stunTicks = 3;
                h.estado = "aturdido";
                h.lesionPendiente = peorLesion(h.lesionPendiente, rand.nextDouble() < 0.2 ? LESION_GRAVE : LESION_MODERADA);
                h.deltaFelicidad -= 20;
                afectados.add(h.caballoId);
                evento.put("caballo_id", h.caballoId);
                descripcion = "¡Un rayo cae sobre " + h.nombre + "! Queda aturdido y lesionado.";
            }
            case BOMBA -> {
                SimHorse centro = corriendo.get(rand.nextInt(corriendo.size()));
                double x = centro.pos;
                List<String> nombres = new ArrayList<>();
                for (SimHorse h : corriendo) {
                    if (Math.abs(h.pos - x) <= 80) {
                        h.pos = Math.max(0, h.pos - 100);
                        h.stunTicks = Math.max(h.stunTicks, 1);
                        h.estado = "aturdido";
                        if (rand.nextDouble() < 0.4) h.lesionPendiente = peorLesion(h.lesionPendiente, LESION_LEVE);
                        h.deltaFelicidad -= 10;
                        afectados.add(h.caballoId);
                        nombres.add(h.nombre);
                    }
                }
                descripcion = "¡Explota una bomba en la pista! Retroceden: " + String.join(", ", nombres) + ".";
            }
            case ENVENENAMIENTO -> {
                List<SimHorse> candidatos = corriendo.stream().filter(h -> !h.envenenado).toList();
                SimHorse h = (candidatos.isEmpty() ? corriendo : candidatos).get(rand.nextInt((candidatos.isEmpty() ? corriendo : candidatos).size()));
                h.envenenado = true;
                h.venenoMult = Math.min(h.venenoMult, 0.75);
                if (h.stunTicks == 0) h.estado = "envenenado";
                h.lesionPendiente = peorLesion(h.lesionPendiente, LESION_LEVE);
                h.deltaFelicidad -= 10;
                afectados.add(h.caballoId);
                evento.put("caballo_id", h.caballoId);
                descripcion = h.nombre + " fue envenenado y se debilita cada vez más.";
            }
            case ABDUCCION -> {
                SimHorse h = corriendo.get(rand.nextInt(corriendo.size()));
                h.dnf = true;
                h.estado = "abducido";
                h.deltaFelicidad -= 15;
                afectados.add(h.caballoId);
                evento.put("caballo_id", h.caballoId);
                descripcion = "¡Un OVNI abduce a " + h.nombre + "! Abandona la carrera (volverá al establo).";
            }
            case MUERTE_SUBITA -> {
                SimHorse h = corriendo.get(rand.nextInt(corriendo.size()));
                h.dnf = true;
                h.estado = "colapso";
                h.lesionPendiente = LESION_GRAVE;
                h.deltaFelicidad -= 30;
                afectados.add(h.caballoId);
                evento.put("caballo_id", h.caballoId);
                descripcion = "¡" + h.nombre + " sufre un colapso súbito y no puede terminar! Queda con una lesión grave.";
            }
            default -> {
                return null;
            }
        }

        evento.put("afectados", afectados);
        evento.put("descripcion", descripcion);
        return evento;
    }
}
