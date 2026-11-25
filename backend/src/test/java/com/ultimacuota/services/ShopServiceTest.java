package com.ultimacuota.services;

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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestDataCleaner.class, Fixtures.class})
class ShopServiceTest {

    @Autowired private ShopService shopService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CaballoRepository caballoRepository;
    @Autowired private InventarioRepository inventarioRepository;
    @Autowired private TransaccionSaldoRepository transaccionRepository;
    @Autowired private TestDataCleaner cleaner;
    @Autowired private Fixtures fx;

    @BeforeEach
    void setUp() {
        cleaner.limpiar();
    }

    private int cantidad(Usuario u, ItemTienda item) {
        return inventarioRepository.findByUsuarioIdAndItemId(u.getId(), item.getId()).map(Inventario::getCantidad).orElse(0);
    }

    @Test
    @DisplayName("Comprar descuenta el total, suma al inventario y registra la transacción")
    void comprar() {
        Usuario u = fx.usuario(1000);
        ItemTienda avena = fx.item("comida", 0, 20, 8, 140);

        shopService.comprar(avena.getId(), 3, u.getId());
        shopService.comprar(avena.getId(), 1, u.getId());

        assertEquals(0, usuarioRepository.findById(u.getId()).orElseThrow().getSaldo().compareTo(BigDecimal.valueOf(440)));
        assertEquals(4, cantidad(u, avena));
        assertEquals(2, transaccionRepository.findAll().stream().filter(t -> "compra_tienda".equals(t.getTipo())).count());
    }

    @Test
    @DisplayName("Sin saldo o con cantidad inválida no se compra")
    void comprarInvalido() {
        Usuario u = fx.usuario(100);
        ItemTienda cirugia = fx.item("curacion", 3, 0, 10, 1200);
        assertThrows(InsufficientBalanceException.class, () -> shopService.comprar(cirugia.getId(), 1, u.getId()));
        assertThrows(IllegalArgumentException.class, () -> shopService.comprar(cirugia.getId(), 0, u.getId()));
        assertEquals(0, cantidad(u, cirugia));
    }

    @Test
    @DisplayName("Una curación solo sana lesiones de su nivel o menor")
    void curacionPorNivel() {
        Usuario u = fx.usuario(10000);
        Caballo c = fx.caballo(u);
        c.setLesion("moderada");
        caballoRepository.save(c);

        ItemTienda vendaje = fx.item("curacion", 1, 0, 0, 150);
        ItemTienda kit = fx.item("curacion", 2, 0, 5, 450);
        shopService.comprar(vendaje.getId(), 1, u.getId());
        shopService.comprar(kit.getId(), 2, u.getId());

        assertThrows(IllegalArgumentException.class, () -> shopService.usar(vendaje.getId(), c.getId(), u.getId()),
                "Un vendaje no cura una lesión moderada");
        assertEquals(1, cantidad(u, vendaje), "Un uso fallido no gasta el producto");

        shopService.usar(kit.getId(), c.getId(), u.getId());
        Caballo curado = caballoRepository.findById(c.getId()).orElseThrow();
        assertNull(curado.getLesion());
        assertEquals(75, curado.getFelicidad());
        assertEquals(1, cantidad(u, kit));

        assertThrows(IllegalArgumentException.class, () -> shopService.usar(kit.getId(), c.getId(), u.getId()),
                "No se puede curar un caballo sano");
    }

    @Test
    @DisplayName("La comida baja la fatiga y sube la felicidad sin pasar de 0 ni de 100")
    void comidaConLimites() {
        Usuario u = fx.usuario(10000);
        Caballo c = fx.caballo(u);
        c.setFatiga(15);
        c.setFelicidad(95);
        caballoRepository.save(c);

        ItemTienda alfalfa = fx.item("comida", 0, 35, 15, 320);
        shopService.comprar(alfalfa.getId(), 2, u.getId());
        shopService.usar(alfalfa.getId(), c.getId(), u.getId());

        Caballo alimentado = caballoRepository.findById(c.getId()).orElseThrow();
        assertEquals(0, alimentado.getFatiga());
        assertEquals(100, alimentado.getFelicidad());

        assertThrows(IllegalArgumentException.class, () -> shopService.usar(alfalfa.getId(), c.getId(), u.getId()),
                "Un caballo descansado y feliz no necesita comida");
    }

    @Test
    @DisplayName("No se puede usar un producto en el caballo de otro usuario ni sin tenerlo")
    void usarAjenoOSinStock() {
        Usuario ana = fx.usuario(1000);
        Usuario beto = fx.usuario(1000);
        Caballo deBeto = fx.caballo(beto);
        deBeto.setFatiga(50);
        caballoRepository.save(deBeto);
        ItemTienda heno = fx.item("comida", 0, 10, 3, 60);

        assertThrows(ResourceNotFoundException.class, () -> shopService.usar(heno.getId(), deBeto.getId(), ana.getId()));
        shopService.comprar(heno.getId(), 1, ana.getId());
        assertThrows(ResourceNotFoundException.class, () -> shopService.usar(heno.getId(), deBeto.getId(), ana.getId()));
    }
}
