package com.ultimacuota.support;

import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/** Crea datos de prueba con nombres únicos. */
@Component
public class Fixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final UsuarioRepository usuarioRepository;
    private final CaballoRepository caballoRepository;
    private final JineteRepository jineteRepository;
    private final ItemTiendaRepository itemRepository;

    public Fixtures(UsuarioRepository usuarioRepository, CaballoRepository caballoRepository,
                    JineteRepository jineteRepository, ItemTiendaRepository itemRepository) {
        this.usuarioRepository = usuarioRepository;
        this.caballoRepository = caballoRepository;
        this.jineteRepository = jineteRepository;
        this.itemRepository = itemRepository;
    }

    public Usuario usuario(double saldo) {
        int n = SEQ.incrementAndGet();
        return usuarioRepository.save(Usuario.builder()
                .username("user_" + n).email("user_" + n + "@test.com")
                .passwordHash("x").saldo(BigDecimal.valueOf(saldo)).build());
    }

    public Caballo caballo(Usuario dueno) {
        return caballoRepository.save(Caballo.builder()
                .nombre("Caballo_" + SEQ.incrementAndGet()).edad(4)
                .velocidad(50).resistencia(50).corazon(50)
                .fatiga(0).carrerasTotales(0).victorias(0)
                .propietario(dueno).esBot(dueno == null).build());
    }

    public Jinete jinete(int habilidad, double precio) {
        return jineteRepository.save(Jinete.builder()
                .nombre("Jinete_" + SEQ.incrementAndGet()).habilidad(habilidad).peso(52)
                .precioContratacion(BigDecimal.valueOf(precio)).build());
    }

    public ItemTienda item(String categoria, int curaNivel, int reduceFatiga, int aumentaFelicidad, double precio) {
        int n = SEQ.incrementAndGet();
        return itemRepository.save(ItemTienda.builder()
                .codigo("item_" + n).nombre("Item " + n).categoria(categoria).calidad("estandar")
                .precio(BigDecimal.valueOf(precio)).curaNivel(curaNivel)
                .reduceFatiga(reduceFatiga).aumentaFelicidad(aumentaFelicidad).build());
    }
}
