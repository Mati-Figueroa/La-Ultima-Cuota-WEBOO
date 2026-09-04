package com.ultimacuota.services;

import com.ultimacuota.exceptions.InsufficientBalanceException;
import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final CaballoRepository caballoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransaccionSaldoRepository transaccionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Map<String, Object>> getOnSale(String search, String sort) {
        return caballoRepository.findOnSaleWithSearch(search, sort)
                .stream().map(this::toMarketHorseMap).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> buyHorse(Long horseId, Long userId) {
        Caballo horse = caballoRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado"));

        if (!horse.getEnVenta() || horse.getPrecioVenta() == null) {
            throw new IllegalArgumentException("Este caballo no está en venta");
        }
        if (horse.getPropietario() != null && horse.getPropietario().getId().equals(userId)) {
            throw new IllegalArgumentException("No puedes comprar tu propio caballo");
        }

        Query saldoQuery = entityManager.createNativeQuery(
                "SELECT saldo FROM usuarios WHERE id = :id FOR UPDATE");
        saldoQuery.setParameter("id", userId);
        Object saldoResult = saldoQuery.getSingleResult();
        BigDecimal buyerSaldo = new BigDecimal(saldoResult.toString());
        BigDecimal price = horse.getPrecioVenta();

        if (buyerSaldo.compareTo(price) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Saldo insuficiente. Necesitas $%,.0f CC", price));
        }

        Long sellerId = horse.getPropietario().getId();

        Usuario buyer = usuarioRepository.getReferenceById(userId);
        buyer.setSaldo(buyer.getSaldo().subtract(price));
        usuarioRepository.save(buyer);

        Usuario seller = usuarioRepository.getReferenceById(sellerId);
        seller.setSaldo(seller.getSaldo().add(price));
        usuarioRepository.save(seller);

        caballoRepository.transfer(horseId, userId);

        TransaccionSaldo buyerTx = TransaccionSaldo.builder()
                .usuario(buyer)
                .tipo("compra_caballo")
                .monto(price.negate())
                .saldoResultante(buyer.getSaldo())
                .referenciaTabla("caballos")
                .referenciaId(horseId)
                .build();
        transaccionRepository.save(buyerTx);

        TransaccionSaldo sellerTx = TransaccionSaldo.builder()
                .usuario(seller)
                .tipo("venta_caballo")
                .monto(price)
                .saldoResultante(seller.getSaldo())
                .referenciaTabla("caballos")
                .referenciaId(horseId)
                .build();
        transaccionRepository.save(sellerTx);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Caballo comprado exitosamente");
        result.put("saldo", buyer.getSaldo());
        return result;
    }

    private Map<String, Object> toMarketHorseMap(Caballo c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("nombre", c.getNombre());
        map.put("edad", c.getEdad());
        map.put("fatiga", c.getFatiga());
        map.put("carreras_totales", c.getCarrerasTotales());
        map.put("victorias", c.getVictorias());
        map.put("posicion_promedio", c.getPosicionPromedio());
        map.put("precio_venta", c.getPrecioVenta());
        map.put("created_at", c.getCreatedAt());
        map.put("dueno_username", c.getPropietario() != null ? c.getPropietario().getUsername() : null);
        return map;
    }
}
