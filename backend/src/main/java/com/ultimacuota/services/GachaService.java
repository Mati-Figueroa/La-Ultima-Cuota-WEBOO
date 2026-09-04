package com.ultimacuota.services;

import com.ultimacuota.exceptions.InsufficientBalanceException;
import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GachaService {

    private static final String[] CHILEAN_NAMES = {
        "Huasito", "Cuequita", "Chorito", "Papayero", "Lonca",
        "Ñusta", "Paliacay", "Quimey", "Rucumán", "Tilcara",
        "Boquerón", "Caranquil", "Desaguadero", "Epuyen", "Futaleufú",
        "Gualata", "Hualpén", "Icalma", "Llanquihue", "Maitencillo"
    };

    private final CaballoRepository caballoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final TransaccionSaldoRepository transaccionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Map<String, Object> pullHorse(Long userId) {
        BigDecimal gachaCost = configuracionRepository.findByClave("gacha_cost")
                .map(c -> new BigDecimal(c.getValor()))
                .orElse(BigDecimal.valueOf(300));

        Query saldoQuery = entityManager.createNativeQuery(
                "SELECT saldo FROM usuarios WHERE id = :id FOR UPDATE");
        saldoQuery.setParameter("id", userId);
        Object saldoResult = saldoQuery.getSingleResult();
        BigDecimal userSaldo = new BigDecimal(saldoResult.toString());

        if (userSaldo.compareTo(gachaCost) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Saldo insuficiente. Necesitas $%,.0f CC, tienes $%,.0f CC", gachaCost, userSaldo));
        }

        Usuario usuario = usuarioRepository.getReferenceById(userId);
        usuario.setSaldo(usuario.getSaldo().subtract(gachaCost));
        usuarioRepository.save(usuario);

        Random rand = new Random();
        String nombre = CHILEAN_NAMES[rand.nextInt(CHILEAN_NAMES.length)];
        int edad = rand.nextInt(6) + 2;
        int velocidad = rand.nextInt(101);
        int resistencia = rand.nextInt(101);
        int corazon = rand.nextInt(101);

        Caballo horse = Caballo.builder()
                .propietario(usuario)
                .nombre(nombre)
                .edad(edad)
                .velocidad(velocidad)
                .resistencia(resistencia)
                .corazon(corazon)
                .build();
        horse = caballoRepository.save(horse);

        TransaccionSaldo tx = TransaccionSaldo.builder()
                .usuario(usuario)
                .tipo("compra_caballo")
                .monto(gachaCost.negate())
                .saldoResultante(usuario.getSaldo())
                .referenciaTabla("caballos")
                .referenciaId(horse.getId())
                .build();
        transaccionRepository.save(tx);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> horseMap = new HashMap<>();
        horseMap.put("id", horse.getId());
        horseMap.put("nombre", horse.getNombre());
        horseMap.put("edad", horse.getEdad());
        result.put("caballo", horseMap);
        result.put("saldo", usuario.getSaldo());
        return result;
    }
}
