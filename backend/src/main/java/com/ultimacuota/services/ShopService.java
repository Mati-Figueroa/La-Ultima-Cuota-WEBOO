package com.ultimacuota.services;

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
public class ShopService {

    public static final int MAX_CANTIDAD_COMPRA = 99;

    private final ItemTiendaRepository itemRepository;
    private final InventarioRepository inventarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final CaballoRepository caballoRepository;
    private final TransaccionSaldoRepository transaccionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Map<String, Object>> listarItems() {
        return itemRepository.findAllByOrderByCategoriaAscPrecioAsc().stream()
                .map(ShopService::itemMap).collect(Collectors.toList());
    }

    @Transactional
    public List<Map<String, Object>> inventario(Long userId) {
        return inventarioRepository.findByUsuarioConItems(userId).stream().map(inv -> {
            Map<String, Object> m = itemMap(inv.getItem());
            m.put("item_id", inv.getItem().getId());
            m.put("cantidad", inv.getCantidad());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> comprar(Long itemId, Integer cantidadSolicitada, Long userId) {
        int cantidad = cantidadSolicitada == null ? 1 : cantidadSolicitada;
        if (cantidad < 1 || cantidad > MAX_CANTIDAD_COMPRA) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + MAX_CANTIDAD_COMPRA);
        }
        ItemTienda item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        BigDecimal saldo = bloquearSaldo(userId);
        BigDecimal total = item.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        if (saldo.compareTo(total) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Saldo insuficiente. Necesitas $%,.0f CC, tienes $%,.0f CC", total, saldo));
        }

        Usuario usuario = usuarioRepository.getReferenceById(userId);
        usuario.setSaldo(usuario.getSaldo().subtract(total));
        usuarioRepository.save(usuario);

        int nuevaCantidad = sumarAlInventario(usuario, item, cantidad);

        transaccionRepository.save(TransaccionSaldo.builder()
                .usuario(usuario)
                .tipo("compra_tienda")
                .monto(total.negate())
                .saldoResultante(usuario.getSaldo())
                .referenciaTabla("items_tienda")
                .referenciaId(itemId)
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("message", String.format("Compraste %d × %s", cantidad, item.getNombre()));
        result.put("saldo", usuario.getSaldo());
        result.put("cantidad_en_inventario", nuevaCantidad);
        return result;
    }

    @Transactional
    public Map<String, Object> usar(Long itemId, Long caballoId, Long userId) {
        if (caballoId == null) throw new IllegalArgumentException("Debes elegir un caballo");
        bloquearSaldo(userId); // serializa las operaciones de inventario del usuario

        Inventario inv = inventarioRepository.findByUsuarioIdAndItemId(userId, itemId)
                .filter(i -> i.getCantidad() > 0)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes ese producto en tu inventario"));
        ItemTienda item = inv.getItem();

        Caballo caballo = caballoRepository.findById(caballoId)
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado en tu establo"));
        if (caballo.getPropietario() == null || !caballo.getPropietario().getId().equals(userId)) {
            throw new ResourceNotFoundException("Caballo no encontrado en tu establo");
        }

        int fatiga = caballo.getFatiga() != null ? caballo.getFatiga() : 0;
        int felicidad = caballo.getFelicidad() != null ? caballo.getFelicidad() : 70;
        List<String> efectos = new ArrayList<>();

        if ("curacion".equals(item.getCategoria())) {
            if (caballo.getLesion() == null) {
                throw new IllegalArgumentException(caballo.getNombre() + " no está lesionado");
            }
            int nivel = RaceEventEngine.nivelLesion(caballo.getLesion());
            if (item.getCuraNivel() < nivel) {
                throw new IllegalArgumentException(item.getNombre() + " no alcanza para curar una lesión " + caballo.getLesion());
            }
            efectos.add("sanó su lesión " + caballo.getLesion());
            caballo.setLesion(null);
            caballo.setLesionHasta(null);
        } else if (fatiga == 0 && felicidad >= 100) {
            throw new IllegalArgumentException(caballo.getNombre() + " ya está descansado y feliz; no lo necesita");
        }

        if (item.getReduceFatiga() > 0 && fatiga > 0) {
            int nueva = Math.max(0, fatiga - item.getReduceFatiga());
            efectos.add("fatiga " + fatiga + " → " + nueva);
            caballo.setFatiga(nueva);
        }
        if (item.getAumentaFelicidad() > 0 && felicidad < 100) {
            int nueva = Math.min(100, felicidad + item.getAumentaFelicidad());
            efectos.add("felicidad " + felicidad + " → " + nueva);
            caballo.setFelicidad(nueva);
        }
        caballoRepository.save(caballo);

        inv.setCantidad(inv.getCantidad() - 1);
        inventarioRepository.save(inv);

        Map<String, Object> caballoMap = new HashMap<>();
        caballoMap.put("id", caballo.getId());
        caballoMap.put("nombre", caballo.getNombre());
        caballoMap.put("fatiga", caballo.getFatiga());
        HorseConditionService.agregarCondicion(caballoMap, caballo);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Usaste " + item.getNombre() + " en " + caballo.getNombre()
                + (efectos.isEmpty() ? "" : ": " + String.join(", ", efectos)));
        result.put("caballo", caballoMap);
        result.put("cantidad_restante", inv.getCantidad());
        return result;
    }

    /** Suma ítems al inventario; el llamador debe tener bloqueada la fila del usuario. */
    int sumarAlInventario(Usuario usuario, ItemTienda item, int cantidad) {
        Inventario inv = inventarioRepository.findByUsuarioIdAndItemId(usuario.getId(), item.getId())
                .orElseGet(() -> Inventario.builder().usuario(usuario).item(item).cantidad(0).build());
        inv.setCantidad(inv.getCantidad() + cantidad);
        inventarioRepository.save(inv);
        return inv.getCantidad();
    }

    private BigDecimal bloquearSaldo(Long userId) {
        List<?> rows = entityManager.createNativeQuery("SELECT saldo FROM usuarios WHERE id = :id FOR UPDATE")
                .setParameter("id", userId).getResultList();
        if (rows.isEmpty()) throw new ResourceNotFoundException("Usuario no encontrado");
        return rows.get(0) == null ? BigDecimal.ZERO : new BigDecimal(rows.get(0).toString());
    }

    public static Map<String, Object> itemMap(ItemTienda i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", i.getId());
        m.put("codigo", i.getCodigo());
        m.put("nombre", i.getNombre());
        m.put("descripcion", i.getDescripcion());
        m.put("categoria", i.getCategoria());
        m.put("calidad", i.getCalidad());
        m.put("precio", i.getPrecio());
        m.put("cura_nivel", i.getCuraNivel());
        m.put("reduce_fatiga", i.getReduceFatiga());
        m.put("aumenta_felicidad", i.getAumentaFelicidad());
        return m;
    }
}
