package com.ultimacuota.services;

import com.ultimacuota.dto.InscribeRequest;
import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import com.ultimacuota.support.Fixtures;
import com.ultimacuota.support.TestDataCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestDataCleaner.class, Fixtures.class})
class HorseConditionServiceTest {

    @Autowired private HorseConditionService conditionService;
    @Autowired private JockeyService jockeyService;
    @Autowired private RaceService raceService;
    @Autowired private CaballoRepository caballoRepository;
    @Autowired private CarreraRepository carreraRepository;
    @Autowired private JineteRepository jineteRepository;
    @Autowired private TestDataCleaner cleaner;
    @Autowired private Fixtures fx;

    @BeforeEach
    void setUp() {
        cleaner.limpiar();
    }

    private Caballo recargar(Caballo c) {
        return caballoRepository.findById(c.getId()).orElseThrow();
    }

    private static Map<String, Object> resultado(Caballo c, int posicion, boolean abandono, String lesion, int deltaFelicidad, int fatigaExtra) {
        Map<String, Object> r = new HashMap<>();
        r.put("caballo_id", c.getId());
        r.put("posicion", posicion);
        r.put("abandono", abandono);
        r.put("lesion_pendiente", lesion);
        r.put("delta_felicidad", deltaFelicidad);
        r.put("fatiga_extra", fatigaExtra);
        return r;
    }

    @Test
    @DisplayName("Las lesiones leves y moderadas sanan solas al vencer; la grave no")
    void curacionNatural() {
        Usuario u = fx.usuario(0);
        Caballo leve = fx.caballo(u);
        Caballo grave = fx.caballo(u);

        assertTrue(conditionService.aplicarLesion(leve.getId(), "leve"));
        assertTrue(conditionService.aplicarLesion(grave.getId(), "grave"));
        assertNotNull(recargar(leve).getLesionHasta(), "Una lesión leve tiene fecha de curación");
        assertNull(recargar(grave).getLesionHasta(), "Una lesión grave solo sana con un ítem");

        Caballo vencido = recargar(leve);
        vencido.setLesionHasta(LocalDateTime.now().minusMinutes(1));
        caballoRepository.save(vencido);

        conditionService.sanarLesionesVencidas();
        assertNull(recargar(leve).getLesion());
        assertEquals("grave", recargar(grave).getLesion());
    }

    @Test
    @DisplayName("Una lesión nueva solo reemplaza a una menos grave")
    void lesionNoEmpeoraHaciaAbajo() {
        Caballo c = fx.caballo(fx.usuario(0));
        conditionService.aplicarLesion(c.getId(), "moderada");
        assertFalse(conditionService.aplicarLesion(c.getId(), "leve"));
        assertEquals("moderada", recargar(c).getLesion());
        assertTrue(conditionService.aplicarLesion(c.getId(), "grave"));
        assertEquals("grave", recargar(c).getLesion());
    }

    @Test
    @DisplayName("Tras la carrera: el ganador gana felicidad, los eventos restan, la sequía cansa y el jinete gana experiencia")
    void efectosPostCarrera() {
        Usuario u = fx.usuario(5000);
        Caballo ganador = fx.caballo(u);
        Caballo golpeado = fx.caballo(u);
        Caballo abducido = fx.caballo(u);
        Caballo bot = fx.caballo(null);
        Jinete jinete = fx.jinete(60, 100);
        jockeyService.contratar(jinete.getId(), u.getId());
        jockeyService.asignar(jinete.getId(), ganador.getId(), u.getId());

        conditionService.aplicarResultadoCarrera(List.of(
                resultado(ganador, 1, false, null, 0, 10),
                resultado(golpeado, 2, false, "moderada", -20, 10),
                resultado(bot, 3, false, "grave", -30, 10),
                resultado(abducido, 4, true, null, -15, 10)
        ));

        assertEquals(80, recargar(ganador).getFelicidad());
        assertEquals(10, recargar(ganador).getFatiga());
        assertEquals("moderada", recargar(golpeado).getLesion());
        assertEquals(53, recargar(golpeado).getFelicidad(), "70 - 20 del rayo + 3 por quedar en el podio");
        assertEquals(55, recargar(abducido).getFelicidad(), "Un abandono solo resta lo del evento");
        assertNull(recargar(bot).getLesion(), "Los bots no sufren lesiones");
        assertEquals(70, recargar(bot).getFelicidad());
        assertEquals(1, jineteRepository.findById(jinete.getId()).orElseThrow().getExperiencia());
    }

    @Test
    @DisplayName("Un caballo lesionado o muy triste no puede inscribirse")
    void inscripcionBloqueada() {
        Usuario u = fx.usuario(1000);
        Caballo lesionado = fx.caballo(u);
        Caballo triste = fx.caballo(u);
        conditionService.aplicarLesion(lesionado.getId(), "leve");
        Caballo t = recargar(triste);
        t.setFelicidad(5);
        caballoRepository.save(t);

        Carrera carrera = carreraRepository.save(Carrera.builder()
                .nombre("Carrera de prueba").estado("programada")
                .fechaProgramada(LocalDateTime.now().plusHours(1)).cupoMaximo(12)
                .tieneInteraccionHumana(true).build());

        InscribeRequest req = new InscribeRequest();
        req.setCaballoId(lesionado.getId());
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () -> raceService.inscribe(carrera.getId(), req, u.getId()));
        assertTrue(e1.getMessage().contains("lesión"));

        req.setCaballoId(triste.getId());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> raceService.inscribe(carrera.getId(), req, u.getId()));
        assertTrue(e2.getMessage().contains("triste"));
    }
}
