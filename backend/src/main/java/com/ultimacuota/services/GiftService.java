package com.ultimacuota.services;

import com.ultimacuota.dto.GiftRequest;
import com.ultimacuota.exceptions.InsufficientBalanceException;
import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GiftService {

    public static final int MAX_MENSAJE = 200;

    private final RegaloRepository regaloRepository;
    private final UsuarioRepository usuarioRepository;
    private final InventarioRepository inventarioRepository;
    private final ItemTiendaRepository itemRepository;
    private final TransaccionSaldoRepository transaccionRepository;
    private final ShopService shopService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Map<String, Object> enviar(GiftRequest request, Long remitenteId) {
        if (request == null || request.getDestinatarioId() == null) {
            throw new IllegalArgumentException("Debes elegir a quién enviar el regalo");
        }
        Long destinatarioId = request.getDestinatarioId();
        if (destinatarioId.equals(remitenteId)) {
            throw new IllegalArgumentException("No puedes enviarte un regalo a ti mismo");
        }

        BigDecimal monto = request.getMonto() == null ? BigDecimal.ZERO : request.getMonto();
        if (monto.signum() < 0) throw new IllegalArgumentException("El monto no puede ser negativo");
        if (monto.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto admite como máximo 2 decimales");
        }
        int cantidad = request.getItemId() == null ? 0 : (request.getCantidad() == null ? 1 : request.getCantidad());
        if (request.getItemId() != null && cantidad < 1) {
            throw new IllegalArgumentException("La cantidad del producto debe ser al menos 1");
        }
        if (monto.signum() == 0 && request.getItemId() == null) {
            throw new IllegalArgumentException("El regalo debe incluir dinero o un producto");
        }
        String mensaje = request.getMensaje() == null ? null : request.getMensaje().trim();
        if (mensaje != null && mensaje.length() > MAX_MENSAJE) {
            throw new IllegalArgumentException("El mensaje no puede superar " + MAX_MENSAJE + " caracteres");
        }
        if (mensaje != null && mensaje.isEmpty()) mensaje = null;

        // Bloqueamos a ambos usuarios en orden de id para evitar deadlocks.
        Map<Long, BigDecimal> saldos = bloquearUsuarios(remitenteId, destinatarioId);
        if (!saldos.containsKey(destinatarioId)) throw new ResourceNotFoundException("Usuario destinatario no encontrado");
        if (!saldos.containsKey(remitenteId)) throw new ResourceNotFoundException("Usuario no encontrado");
        if (saldos.get(remitenteId).compareTo(monto) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Saldo insuficiente. Quieres regalar $%,.0f CC y tienes $%,.0f CC", monto, saldos.get(remitenteId)));
        }

        Usuario remitente = usuarioRepository.getReferenceById(remitenteId);
        Usuario destinatario = usuarioRepository.getReferenceById(destinatarioId);

        ItemTienda item = null;
        if (request.getItemId() != null) {
            item = itemRepository.findById(request.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
            Inventario propio = inventarioRepository.findByUsuarioIdAndItemId(remitenteId, item.getId())
                    .orElseThrow(() -> new IllegalArgumentException("No tienes ese producto en tu inventario"));
            if (propio.getCantidad() < cantidad) {
                throw new IllegalArgumentException("Solo tienes " + propio.getCantidad() + " de " + item.getNombre());
            }
            propio.setCantidad(propio.getCantidad() - cantidad);
            inventarioRepository.save(propio);
            shopService.sumarAlInventario(destinatario, item, cantidad);
        }

        Regalo regalo = regaloRepository.save(Regalo.builder()
                .remitente(remitente)
                .destinatario(destinatario)
                .monto(monto)
                .item(item)
                .cantidad(cantidad)
                .mensaje(mensaje)
                .build());

        if (monto.signum() > 0) {
            remitente.setSaldo(remitente.getSaldo().subtract(monto));
            destinatario.setSaldo(destinatario.getSaldo().add(monto));
            usuarioRepository.save(remitente);
            usuarioRepository.save(destinatario);

            transaccionRepository.save(TransaccionSaldo.builder()
                    .usuario(remitente).tipo("regalo_enviado").monto(monto.negate())
                    .saldoResultante(remitente.getSaldo())
                    .referenciaTabla("regalos").referenciaId(regalo.getId()).build());
            transaccionRepository.save(TransaccionSaldo.builder()
                    .usuario(destinatario).tipo("regalo_recibido").monto(monto)
                    .saldoResultante(destinatario.getSaldo())
                    .referenciaTabla("regalos").referenciaId(regalo.getId()).build());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Regalo enviado a " + destinatario.getUsername());
        result.put("regalo", regaloMap(regalo));
        result.put("saldo", remitente.getSaldo());
        return result;
    }

    @Transactional
    public List<Map<String, Object>> listar(Long userId, String tipo) {
        List<Regalo> regalos = "enviados".equalsIgnoreCase(tipo)
                ? regaloRepository.findEnviados(userId)
                : regaloRepository.findRecibidos(userId);
        return regalos.stream().map(GiftService::regaloMap).collect(Collectors.toList());
    }

    private Map<Long, BigDecimal> bloquearUsuarios(Long a, Long b) {
        List<Long> orden = new ArrayList<>(List.of(a, b));
        Collections.sort(orden);
        Map<Long, BigDecimal> saldos = new HashMap<>();
        for (Long id : orden) {
            List<?> rows = entityManager.createNativeQuery("SELECT saldo FROM usuarios WHERE id = :id FOR UPDATE")
                    .setParameter("id", id).getResultList();
            if (!rows.isEmpty()) {
                saldos.put(id, rows.get(0) == null ? BigDecimal.ZERO : new BigDecimal(rows.get(0).toString()));
            }
        }
        return saldos;
    }

    static Map<String, Object> regaloMap(Regalo r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("monto", r.getMonto());
        m.put("cantidad", r.getCantidad());
        m.put("mensaje", r.getMensaje());
        m.put("created_at", r.getCreatedAt());
        m.put("remitente", Map.of("id", r.getRemitente().getId(), "username", r.getRemitente().getUsername()));
        m.put("destinatario", Map.of("id", r.getDestinatario().getId(), "username", r.getDestinatario().getUsername()));
        if (r.getItem() != null) {
            m.put("item", Map.of("id", r.getItem().getId(), "nombre", r.getItem().getNombre(), "categoria", r.getItem().getCategoria()));
        } else {
            m.put("item", null);
        }
        return m;
    }
}
