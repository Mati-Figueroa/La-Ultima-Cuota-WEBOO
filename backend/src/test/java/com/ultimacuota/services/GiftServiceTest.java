package com.ultimacuota.services;

import com.ultimacuota.dto.GiftRequest;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestDataCleaner.class, Fixtures.class})
class GiftServiceTest {

    @Autowired private GiftService giftService;
    @Autowired private ShopService shopService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private InventarioRepository inventarioRepository;
    @Autowired private TransaccionSaldoRepository transaccionRepository;
    @Autowired private TestDataCleaner cleaner;
    @Autowired private Fixtures fx;

    @BeforeEach
    void setUp() {
        cleaner.limpiar();
    }

    private BigDecimal saldo(Long id) {
        return usuarioRepository.findById(id).orElseThrow().getSaldo();
    }

    private static GiftRequest regalo(Long destinatario, double monto) {
        GiftRequest r = new GiftRequest();
        r.setDestinatarioId(destinatario);
        r.setMonto(BigDecimal.valueOf(monto));
        return r;
    }

    @Test
    @DisplayName("Regalar dinero mueve el saldo y registra una transacción en cada usuario")
    void regalarDinero() {
        Usuario ana = fx.usuario(1000);
        Usuario beto = fx.usuario(200);
        GiftRequest req = regalo(beto.getId(), 250);
        req.setMensaje("¡Suerte en la próxima carrera!");

        giftService.enviar(req, ana.getId());

        assertEquals(0, saldo(ana.getId()).compareTo(BigDecimal.valueOf(750)));
        assertEquals(0, saldo(beto.getId()).compareTo(BigDecimal.valueOf(450)));
        assertTrue(transaccionRepository.findAll().stream().anyMatch(t -> "regalo_enviado".equals(t.getTipo())
                && t.getUsuario().getId().equals(ana.getId()) && t.getMonto().compareTo(BigDecimal.valueOf(-250)) == 0));
        assertTrue(transaccionRepository.findAll().stream().anyMatch(t -> "regalo_recibido".equals(t.getTipo())
                && t.getUsuario().getId().equals(beto.getId()) && t.getMonto().compareTo(BigDecimal.valueOf(250)) == 0));

        List<Map<String, Object>> recibidos = giftService.listar(beto.getId(), "recibidos");
        assertEquals(1, recibidos.size());
        assertEquals("¡Suerte en la próxima carrera!", recibidos.get(0).get("mensaje"));
        assertEquals(1, giftService.listar(ana.getId(), "enviados").size());
        assertTrue(giftService.listar(ana.getId(), "recibidos").isEmpty());
    }

    @Test
    @DisplayName("Regalos inválidos: a uno mismo, vacíos, negativos, sin saldo o a un usuario inexistente")
    void regalosInvalidos() {
        Usuario ana = fx.usuario(100);
        Usuario beto = fx.usuario(100);
        assertThrows(IllegalArgumentException.class, () -> giftService.enviar(regalo(ana.getId(), 10), ana.getId()));
        assertThrows(IllegalArgumentException.class, () -> giftService.enviar(regalo(beto.getId(), 0), ana.getId()));
        assertThrows(IllegalArgumentException.class, () -> giftService.enviar(regalo(beto.getId(), -5), ana.getId()));
        assertThrows(InsufficientBalanceException.class, () -> giftService.enviar(regalo(beto.getId(), 101), ana.getId()));
        assertThrows(ResourceNotFoundException.class, () -> giftService.enviar(regalo(999999L, 10), ana.getId()));
        GiftRequest largo = regalo(beto.getId(), 1);
        largo.setMensaje("x".repeat(201));
        assertThrows(IllegalArgumentException.class, () -> giftService.enviar(largo, ana.getId()));

        assertEquals(0, saldo(ana.getId()).compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, saldo(beto.getId()).compareTo(BigDecimal.valueOf(100)));
    }

    @Test
    @DisplayName("Se pueden regalar productos del inventario")
    void regalarProducto() {
        Usuario ana = fx.usuario(1000);
        Usuario beto = fx.usuario(0);
        ItemTienda zanahorias = fx.item("cuidado", 0, 0, 10, 80);
        shopService.comprar(zanahorias.getId(), 3, ana.getId());

        GiftRequest req = new GiftRequest();
        req.setDestinatarioId(beto.getId());
        req.setItemId(zanahorias.getId());
        req.setCantidad(2);
        giftService.enviar(req, ana.getId());

        assertEquals(1, inventarioRepository.findByUsuarioIdAndItemId(ana.getId(), zanahorias.getId()).orElseThrow().getCantidad());
        assertEquals(2, inventarioRepository.findByUsuarioIdAndItemId(beto.getId(), zanahorias.getId()).orElseThrow().getCantidad());

        req.setCantidad(5);
        assertThrows(IllegalArgumentException.class, () -> giftService.enviar(req, ana.getId()), "No se regala más de lo que se tiene");
    }

    @Test
    @DisplayName("Regalos concurrentes nunca dejan saldo negativo y conservan el dinero total")
    void regalosConcurrentes() throws Exception {
        Usuario ana = fx.usuario(1000);
        Usuario beto = fx.usuario(1000);
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();

        for (int i = 0; i < hilos; i++) {
            // La mitad va de Ana a Beto y la otra mitad al revés, para provocar bloqueos cruzados.
            final boolean deAna = i % 2 == 0;
            pool.submit(() -> {
                try {
                    inicio.await();
                    if (deAna) giftService.enviar(regalo(beto.getId(), 150), ana.getId());
                    else giftService.enviar(regalo(ana.getId(), 150), beto.getId());
                    exitos.incrementAndGet();
                } catch (Exception ignored) {
                }
                return null;
            });
        }
        inicio.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "Sin deadlocks: todos los hilos terminan");

        BigDecimal saldoAna = saldo(ana.getId());
        BigDecimal saldoBeto = saldo(beto.getId());
        assertTrue(saldoAna.signum() >= 0 && saldoBeto.signum() >= 0, "Nadie queda con saldo negativo");
        assertEquals(0, saldoAna.add(saldoBeto).compareTo(BigDecimal.valueOf(2000)), "El dinero total se conserva");
        assertEquals(hilos, exitos.get(), "Con saldo de sobra, todos los regalos se completan");
    }
}
