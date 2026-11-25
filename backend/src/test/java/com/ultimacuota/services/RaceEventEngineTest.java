package com.ultimacuota.services;

import com.ultimacuota.services.RaceSimulationService.RaceState;
import com.ultimacuota.services.RaceSimulationService.SimHorse;
import com.ultimacuota.services.RaceSimulationService.TickResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RaceEventEngineTest {

    private static RaceState nuevaCarrera(Random rand, int caballos) {
        List<SimHorse> horses = new ArrayList<>();
        Map<Long, Double> positions = new HashMap<>();
        for (long id = 1; id <= caballos; id++) {
            SimHorse h = new SimHorse(id, rand.nextInt(101), rand.nextInt(101), rand.nextInt(101));
            h.nombre = "Caballo " + id;
            horses.add(h);
            positions.put(id, 0.0);
        }
        RaceState state = new RaceState(horses, positions, 0, true);
        state.plan.addAll(RaceEventEngine.planificar(rand));
        return state;
    }

    /** Corre la carrera completa y devuelve todos los ticks. */
    private static List<TickResult> correr(RaceState state, Random rand) {
        List<TickResult> ticks = new ArrayList<>();
        for (int t = 1; t <= 500; t++) {
            TickResult r = RaceEventEngine.tick(state, rand, t);
            ticks.add(r);
            if (r.results != null) return ticks;
        }
        fail("La carrera no terminó en 500 ticks");
        return ticks;
    }

    @Test
    @DisplayName("1.000 carreras: siempre hay al menos un evento y los resultados son coherentes")
    void milCarrerasCoherentes() {
        Random rand = new Random(42);
        Map<String, Integer> conteo = new HashMap<>();

        for (int carrera = 0; carrera < 1000; carrera++) {
            int n = 6 + rand.nextInt(7);
            RaceState state = nuevaCarrera(rand, n);
            List<TickResult> ticks = correr(state, rand);

            List<Map<String, Object>> eventos = ticks.stream().flatMap(t -> t.eventos.stream()).toList();
            assertFalse(eventos.isEmpty(), "La carrera " + carrera + " no tuvo eventos");
            eventos.forEach(e -> conteo.merge((String) e.get("tipo"), 1, Integer::sum));
            assertTrue(eventos.stream().filter(e -> RaceEventEngine.CLIMAS.contains(e.get("tipo"))).count() <= 1,
                    "Como máximo un evento de clima por carrera");

            List<Map<String, Object>> results = ticks.get(ticks.size() - 1).results;
            assertEquals(n, results.size(), "Todos los caballos aparecen en los resultados");
            assertEquals(n, results.stream().map(r -> r.get("caballo_id")).distinct().count());

            assertNotEquals(Boolean.TRUE, results.get(0).get("abandono"), "El ganador siempre terminó la carrera");
            assertNotNull(results.get(0).get("tiempo"));

            boolean vioAbandono = false;
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> r = results.get(i);
                assertEquals(i + 1, r.get("posicion"));
                boolean abandono = Boolean.TRUE.equals(r.get("abandono"));
                if (abandono) {
                    vioAbandono = true;
                    assertNull(r.get("tiempo"), "Un abandono no tiene tiempo");
                } else {
                    assertFalse(vioAbandono, "Los abandonos van después de todos los que terminaron");
                }
            }
        }

        // Con 1.000 carreras deben aparecer todos los tipos de evento.
        for (String tipo : List.of("lluvia", "barro", "sequia", "rayo", "bomba", "envenenamiento", "abduccion", "muerte_subita")) {
            assertTrue(conteo.getOrDefault(tipo, 0) > 0, "Nunca ocurrió el evento " + tipo);
        }
    }

    @Test
    @DisplayName("Abducción y muerte súbita nunca dejan la carrera sin nadie corriendo")
    void abandonoNuncaElUltimo() {
        Random rand = new Random(7);
        RaceState state = nuevaCarrera(rand, 2);
        state.plan.clear();
        state.horses.get(0).finished = true;

        Map<String, Object> evento = RaceEventEngine.aplicar(state, RaceEventEngine.ABDUCCION, rand, 5);
        assertEquals(RaceEventEngine.RAYO, evento.get("tipo"), "Con un solo caballo corriendo se cambia por un rayo");
        assertFalse(state.horses.get(1).dnf);

        evento = RaceEventEngine.aplicar(state, RaceEventEngine.MUERTE_SUBITA, rand, 6);
        assertEquals(RaceEventEngine.RAYO, evento.get("tipo"));
        assertFalse(state.horses.get(1).dnf);
    }

    @Test
    @DisplayName("Efectos: rayo lesiona y aturde; muerte súbita deja lesión grave; sequía suma fatiga")
    void efectosDeEventos() {
        Random rand = new Random(3);
        RaceState state = nuevaCarrera(rand, 4);

        Map<String, Object> rayo = RaceEventEngine.aplicar(state, RaceEventEngine.RAYO, rand, 3);
        SimHorse golpeado = state.horses.stream().filter(h -> h.caballoId.equals(rayo.get("caballo_id"))).findFirst().orElseThrow();
        assertEquals(3, golpeado.stunTicks);
        assertEquals("aturdido", golpeado.estado);
        assertTrue(RaceEventEngine.nivelLesion(golpeado.lesionPendiente) >= 2);
        assertEquals(-20, golpeado.deltaFelicidad);

        Map<String, Object> colapso = RaceEventEngine.aplicar(state, RaceEventEngine.MUERTE_SUBITA, rand, 4);
        SimHorse caido = state.horses.stream().filter(h -> h.caballoId.equals(colapso.get("caballo_id"))).findFirst().orElseThrow();
        assertTrue(caido.dnf);
        assertEquals("colapso", caido.estado);
        assertEquals(RaceEventEngine.LESION_GRAVE, caido.lesionPendiente);

        RaceEventEngine.aplicar(state, RaceEventEngine.SEQUIA, rand, 5);
        assertEquals(RaceEventEngine.SEQUIA, state.clima);
        state.horses.forEach(h -> assertEquals(10, h.fatigaExtra));

        // Un segundo clima se convierte en un evento puntual.
        Map<String, Object> otro = RaceEventEngine.aplicar(state, RaceEventEngine.LLUVIA, rand, 6);
        assertEquals(RaceEventEngine.RAYO, otro.get("tipo"));
        assertEquals(RaceEventEngine.SEQUIA, state.clima);
    }

    @Test
    @DisplayName("La felicidad, las lesiones y el jinete cambian el multiplicador de velocidad")
    void multiplicadores() {
        assertEquals(0.9, RaceEventEngine.multiplicadorFelicidad(0), 1e-9);
        assertEquals(1.1, RaceEventEngine.multiplicadorFelicidad(100), 1e-9);
        assertEquals(1.0, RaceEventEngine.multiplicadorLesion(null), 1e-9);
        assertEquals(0.6, RaceEventEngine.multiplicadorLesion("grave"), 1e-9);

        SimHorse conJinete = new SimHorse(1L, 50, 50, 50);
        conJinete.jineteEspecialidad = "lluvia";
        SimHorse sinJinete = new SimHorse(2L, 50, 50, 50);
        assertTrue(RaceEventEngine.multiplicadorClima("lluvia", conJinete) > RaceEventEngine.multiplicadorClima("lluvia", sinJinete));
    }
}
