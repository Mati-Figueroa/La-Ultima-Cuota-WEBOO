package com.ultimacuota.concurrency;

import com.ultimacuota.dto.BetRequest;
import com.ultimacuota.dto.InscribeRequest;
import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import com.ultimacuota.services.RaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrencyTest {

    @Autowired
    private RaceService raceService;

    @Autowired
    private CarreraRepository carreraRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CaballoRepository caballoRepository;

    @Autowired
    private InscripcionRepository inscripcionRepository;

    @Autowired
    private ApuestaRepository apuestaRepository;

    private Carrera testRace;

    @BeforeEach
    void setUp() {
        apuestaRepository.deleteAll();
        inscripcionRepository.deleteAll();
        carreraRepository.deleteAll();
        caballoRepository.deleteAll();
        usuarioRepository.deleteAll();

        testRace = Carrera.builder()
                .nombre("Gran Premio Concurrencia")
                .estado("programada")
                .fechaProgramada(LocalDateTime.now().plusHours(1))
                .cupoMaximo(12)
                .tieneInteraccionHumana(true)
                .build();
        testRace = carreraRepository.save(testRace);
    }

    @Test
    @DisplayName("Inscripción Concurrente: 20 usuarios intentan ocupar 12 cupos simultáneamente")
    void testConcurrentInscriptions() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);

        List<Long> userIds = new ArrayList<>();
        List<Long> horseIds = new ArrayList<>();

        for (int i = 1; i <= threadCount; i++) {
            Usuario u = Usuario.builder()
                    .username("runner_" + i)
                    .email("runner_" + i + "@test.com")
                    .passwordHash("pass")
                    .saldo(BigDecimal.valueOf(1000))
                    .build();
            u = usuarioRepository.save(u);
            userIds.add(u.getId());

            Caballo h = Caballo.builder()
                    .nombre("Caballo_" + i)
                    .edad(4)
                    .fatiga(0)
                    .propietario(u)
                    .esBot(false)
                    .carrerasTotales(0)
                    .victorias(0)
                    .build();
            h = caballoRepository.save(h);
            horseIds.add(h.getId());
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startSignal.await();
                    InscribeRequest req = new InscribeRequest();
                    req.setCaballoId(horseIds.get(index));
                    raceService.inscribe(testRace.getId(), req, userIds.get(index));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        assertTrue(doneSignal.await(10, TimeUnit.SECONDS), "Las inscripciones concurrentes deben completar a tiempo");
        executor.shutdown();

        long actualInscriptions = inscripcionRepository.countByCarreraId(testRace.getId());
        assertEquals(12, actualInscriptions, "Deben haberse inscrito exactamente 12 caballos (cupo máximo)");
        assertEquals(12, successCount.get(), "Exactamente 12 hilos debieron tener éxito");
        assertEquals(8, failureCount.get(), "Exactamente 8 hilos debieron fallar al superar el cupo");
    }

    @Test
    @DisplayName("Apuestas Concurrentes: 20 hilos apostando $100 sobre un saldo de $1,000")
    void testConcurrentBets() throws InterruptedException {
        Usuario user = Usuario.builder()
                .username("bettor_concurrency")
                .email("bettor@test.com")
                .passwordHash("pass")
                .saldo(BigDecimal.valueOf(1000))
                .build();
        user = usuarioRepository.save(user);

        Caballo h = Caballo.builder()
                .nombre("CaballoBet")
                .edad(4)
                .fatiga(0)
                .propietario(user)
                .esBot(false)
                .carrerasTotales(0)
                .victorias(0)
                .build();
        h = caballoRepository.save(h);

        InscribeRequest inscribeReq = new InscribeRequest();
        inscribeReq.setCaballoId(h.getId());
        raceService.inscribe(testRace.getId(), inscribeReq, user.getId());

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        final Long userId = user.getId();
        final Long horseId = h.getId();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    BetRequest betReq = new BetRequest();
                    betReq.setCaballoId(horseId);
                    betReq.setMonto(BigDecimal.valueOf(100));
                    raceService.placeBet(testRace.getId(), betReq, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        assertTrue(doneSignal.await(10, TimeUnit.SECONDS), "Las apuestas concurrentes deben completar a tiempo");
        executor.shutdown();

        Usuario finalUser = usuarioRepository.findById(userId).orElseThrow();
        assertEquals(0, finalUser.getSaldo().compareTo(BigDecimal.ZERO), "El saldo final debe ser $0.00 (sin sobregiro ni saldo negativo)");
        assertEquals(10, successCount.get(), "Exactamente 10 apuestas de $100 debieron tener éxito");
        assertEquals(10, failureCount.get(), "Exactamente 10 apuestas debieron fallar por saldo insuficiente");
    }
}
