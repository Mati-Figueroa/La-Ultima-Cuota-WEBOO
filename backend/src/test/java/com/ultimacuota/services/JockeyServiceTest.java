package com.ultimacuota.services;

import com.ultimacuota.exceptions.ConflictException;
import com.ultimacuota.exceptions.InsufficientBalanceException;
import com.ultimacuota.exceptions.ResourceNotFoundException;
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

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestDataCleaner.class, Fixtures.class})
class JockeyServiceTest {

    @Autowired private JockeyService jockeyService;
    @Autowired private JineteRepository jineteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TransaccionSaldoRepository transaccionRepository;
    @Autowired private CaballoRepository caballoRepository;
    @Autowired private TestDataCleaner cleaner;
    @Autowired private Fixtures fx;

    @BeforeEach
    void setUp() {
        cleaner.limpiar();
    }

    private BigDecimal saldo(Usuario u) {
        return usuarioRepository.findById(u.getId()).orElseThrow().getSaldo();
    }

    @Test
    @DisplayName("Contratar cobra el precio, registra la transacción y el jinete queda exclusivo")
    void contratarEsExclusivo() {
        Usuario ana = fx.usuario(1000);
        Usuario beto = fx.usuario(1000);
        Jinete jinete = fx.jinete(80, 600);

        jockeyService.contratar(jinete.getId(), ana.getId());

        assertEquals(0, saldo(ana).compareTo(BigDecimal.valueOf(400)));
        assertEquals(ana.getId(), jineteRepository.findById(jinete.getId()).orElseThrow().getEmpleador().getId());
        assertTrue(transaccionRepository.findAll().stream()
                .anyMatch(t -> "contrato_jinete".equals(t.getTipo()) && t.getMonto().compareTo(BigDecimal.valueOf(-600)) == 0));

        assertThrows(ConflictException.class, () -> jockeyService.contratar(jinete.getId(), beto.getId()));
        assertThrows(ConflictException.class, () -> jockeyService.contratar(jinete.getId(), ana.getId()));
        assertEquals(0, saldo(beto).compareTo(BigDecimal.valueOf(1000)), "Un intento fallido no cobra");
    }

    @Test
    @DisplayName("Sin saldo suficiente no se puede contratar")
    void contratarSinSaldo() {
        Usuario pobre = fx.usuario(100);
        Jinete jinete = fx.jinete(90, 3000);
        assertThrows(InsufficientBalanceException.class, () -> jockeyService.contratar(jinete.getId(), pobre.getId()));
        assertNull(jineteRepository.findById(jinete.getId()).orElseThrow().getEmpleador());
    }

    @Test
    @DisplayName("Hay un máximo de jinetes por usuario")
    void maximoPorUsuario() {
        Usuario rico = fx.usuario(100000);
        for (int i = 0; i < 5; i++) {
            jockeyService.contratar(fx.jinete(50, 100).getId(), rico.getId());
        }
        Jinete sexto = fx.jinete(50, 100);
        assertThrows(ConflictException.class, () -> jockeyService.contratar(sexto.getId(), rico.getId()));
    }

    @Test
    @DisplayName("Dos usuarios contratando el mismo jinete a la vez: solo uno lo consigue")
    void contratacionConcurrente() throws Exception {
        Jinete jinete = fx.jinete(70, 500);
        int hilos = 10;
        Long[] usuarios = new Long[hilos];
        for (int i = 0; i < hilos; i++) usuarios[i] = fx.usuario(1000).getId();

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        for (Long userId : usuarios) {
            pool.submit(() -> {
                try {
                    inicio.await();
                    jockeyService.contratar(jinete.getId(), userId);
                    exitos.incrementAndGet();
                } catch (Exception ignored) {
                }
                return null;
            });
        }
        inicio.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

        assertEquals(1, exitos.get(), "Solo un usuario puede contratar al jinete");
        long cobrados = 0;
        for (Long userId : usuarios) {
            if (usuarioRepository.findById(userId).orElseThrow().getSaldo().compareTo(BigDecimal.valueOf(1000)) != 0) cobrados++;
        }
        assertEquals(1, cobrados, "Solo se cobra a quien lo contrató");
    }

    @Test
    @DisplayName("Asignar solo a caballos propios; un caballo tiene un solo jinete; despedir lo libera")
    void asignarYDespedir() {
        Usuario ana = fx.usuario(5000);
        Usuario beto = fx.usuario(5000);
        Caballo deAna = fx.caballo(ana);
        Caballo deBeto = fx.caballo(beto);
        Jinete j1 = fx.jinete(60, 100);
        Jinete j2 = fx.jinete(70, 100);
        jockeyService.contratar(j1.getId(), ana.getId());
        jockeyService.contratar(j2.getId(), ana.getId());

        assertThrows(ResourceNotFoundException.class, () -> jockeyService.asignar(j1.getId(), deBeto.getId(), ana.getId()));
        assertThrows(ConflictException.class, () -> jockeyService.asignar(j1.getId(), deAna.getId(), beto.getId()),
                "Solo el empleador puede asignar al jinete");

        jockeyService.asignar(j1.getId(), deAna.getId(), ana.getId());
        assertEquals(deAna.getId(), jineteRepository.findById(j1.getId()).orElseThrow().getCaballo().getId());
        assertTrue(jineteRepository.findValidoPorCaballo(deAna.getId()).isPresent());

        // Asignar un segundo jinete al mismo caballo reemplaza al primero.
        jockeyService.asignar(j2.getId(), deAna.getId(), ana.getId());
        assertNull(jineteRepository.findById(j1.getId()).orElseThrow().getCaballo());
        assertEquals(j2.getId(), jineteRepository.findValidoPorCaballo(deAna.getId()).orElseThrow().getId());

        jockeyService.despedir(j2.getId(), ana.getId());
        Jinete liberado = jineteRepository.findById(j2.getId()).orElseThrow();
        assertNull(liberado.getEmpleador());
        assertNull(liberado.getCaballo());
        assertTrue(jineteRepository.findValidoPorCaballo(deAna.getId()).isEmpty());

        // Ahora otro usuario puede contratarlo.
        jockeyService.contratar(j2.getId(), beto.getId());
        assertEquals(beto.getId(), jineteRepository.findById(j2.getId()).orElseThrow().getEmpleador().getId());
    }

    @Test
    @DisplayName("Un jinete no cuenta si el caballo cambió de dueño")
    void jineteNoValidoTrasCambioDeDueno() {
        Usuario ana = fx.usuario(5000);
        Usuario beto = fx.usuario(5000);
        Caballo caballo = fx.caballo(ana);
        Jinete j = fx.jinete(60, 100);
        jockeyService.contratar(j.getId(), ana.getId());
        jockeyService.asignar(j.getId(), caballo.getId(), ana.getId());

        caballo.setPropietario(beto);
        caballoRepository.save(caballo);
        assertTrue(jineteRepository.findValidoPorCaballo(caballo.getId()).isEmpty());
    }
}
