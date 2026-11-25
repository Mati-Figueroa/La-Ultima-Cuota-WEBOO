package com.ultimacuota.services;

import com.ultimacuota.exceptions.ConflictException;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JockeyService {

    private final JineteRepository jineteRepository;
    private final CaballoRepository caballoRepository;
    private final UsuarioRepository usuarioRepository;
    private final InscripcionRepository inscripcionRepository;
    private final TransaccionSaldoRepository transaccionRepository;
    private final ConfiguracionRepository configuracionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public List<Map<String, Object>> listar(String estado) {
        List<Jinete> jinetes = "disponibles".equalsIgnoreCase(estado)
                ? jineteRepository.findDisponibles()
                : jineteRepository.findAllWithRelations();
        return jinetes.stream().map(JockeyService::detalle).collect(Collectors.toList());
    }

    @Transactional
    public List<Map<String, Object>> mios(Long userId) {
        return jineteRepository.findByEmpleadorId(userId).stream()
                .map(JockeyService::detalle).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> obtener(Long id) {
        Jinete j = jineteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jinete no encontrado"));
        return detalle(j);
    }

    @Transactional
    public Map<String, Object> contratar(Long jineteId, Long userId) {
        // Orden de bloqueo: usuario y luego jinete, para serializar sin deadlocks.
        BigDecimal saldo = bloquearSaldo(userId);
        Object empleadorActual = bloquearJinete(jineteId);

        if (empleadorActual != null) {
            if (((Number) empleadorActual).longValue() == userId) {
                throw new ConflictException("Este jinete ya trabaja para ti");
            }
            throw new ConflictException("Este jinete ya fue contratado por otro usuario");
        }

        int maximo = configuracionRepository.findByClave("max_jinetes_usuario")
                .map(c -> Integer.parseInt(c.getValor().trim())).orElse(5);
        if (jineteRepository.countByEmpleadorId(userId) >= maximo) {
            throw new ConflictException("Ya tienes el máximo de " + maximo + " jinetes contratados");
        }

        Jinete jinete = jineteRepository.findById(jineteId).orElseThrow();
        BigDecimal precio = jinete.getPrecioContratacion();
        if (saldo.compareTo(precio) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Saldo insuficiente. Contratar a %s cuesta $%,.0f CC", jinete.getNombre(), precio));
        }

        Usuario usuario = usuarioRepository.getReferenceById(userId);
        usuario.setSaldo(usuario.getSaldo().subtract(precio));
        usuarioRepository.save(usuario);

        jinete.setEmpleador(usuario);
        jinete.setCaballo(null);
        jinete.setFechaContratacion(LocalDateTime.now());
        jineteRepository.save(jinete);

        transaccionRepository.save(TransaccionSaldo.builder()
                .usuario(usuario)
                .tipo("contrato_jinete")
                .monto(precio.negate())
                .saldoResultante(usuario.getSaldo())
                .referenciaTabla("jinetes")
                .referenciaId(jineteId)
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Contrataste a " + jinete.getNombre());
        result.put("jinete", detalle(jinete));
        result.put("saldo", usuario.getSaldo());
        return result;
    }

    @Transactional
    public Map<String, Object> asignar(Long jineteId, Long caballoId, Long userId) {
        bloquearJinete(jineteId);
        Jinete jinete = jineteRepository.findById(jineteId)
                .orElseThrow(() -> new ResourceNotFoundException("Jinete no encontrado"));
        exigirEmpleador(jinete, userId);

        Caballo actual = jinete.getCaballo();
        if (actual != null && inscripcionRepository.isCorriendoAhora(actual.getId())) {
            throw new ConflictException("No puedes mover a un jinete mientras corre una carrera");
        }

        if (caballoId == null) {
            jinete.setCaballo(null);
            jineteRepository.save(jinete);
            return Map.of("message", jinete.getNombre() + " quedó sin caballo asignado", "jinete", detalle(jinete));
        }

        Caballo caballo = caballoRepository.findById(caballoId)
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado en tu establo"));
        if (caballo.getPropietario() == null || !caballo.getPropietario().getId().equals(userId)) {
            throw new ResourceNotFoundException("Caballo no encontrado en tu establo");
        }
        if (Boolean.TRUE.equals(caballo.getEsBot())) {
            throw new IllegalArgumentException("Los caballos bot no pueden tener jinete");
        }
        if (inscripcionRepository.isCorriendoAhora(caballoId)) {
            throw new ConflictException("No puedes cambiar el jinete de un caballo que está corriendo");
        }

        if (actual != null && actual.getId().equals(caballoId)) {
            return Map.of("message", jinete.getNombre() + " ya monta a " + caballo.getNombre(), "jinete", detalle(jinete));
        }

        // Un caballo tiene un solo jinete: liberamos al que tuviera.
        jineteRepository.desasignarDeCaballo(caballoId);
        entityManager.flush();
        jinete.setCaballo(caballo);
        jineteRepository.save(jinete);

        return Map.of("message", jinete.getNombre() + " ahora monta a " + caballo.getNombre(), "jinete", detalle(jinete));
    }

    @Transactional
    public Map<String, Object> despedir(Long jineteId, Long userId) {
        bloquearJinete(jineteId);
        Jinete jinete = jineteRepository.findById(jineteId)
                .orElseThrow(() -> new ResourceNotFoundException("Jinete no encontrado"));
        exigirEmpleador(jinete, userId);
        if (jinete.getCaballo() != null && inscripcionRepository.isCorriendoAhora(jinete.getCaballo().getId())) {
            throw new ConflictException("No puedes despedir a un jinete mientras corre una carrera");
        }

        jinete.setEmpleador(null);
        jinete.setCaballo(null);
        jinete.setFechaContratacion(null);
        jineteRepository.save(jinete);
        return Map.of("message", "Despediste a " + jinete.getNombre() + ". Vuelve a estar disponible.");
    }

    private void exigirEmpleador(Jinete jinete, Long userId) {
        if (jinete.getEmpleador() == null || !jinete.getEmpleador().getId().equals(userId)) {
            throw new ConflictException("Este jinete no trabaja para ti");
        }
    }

    private BigDecimal bloquearSaldo(Long userId) {
        List<?> rows = entityManager.createNativeQuery("SELECT saldo FROM usuarios WHERE id = :id FOR UPDATE")
                .setParameter("id", userId).getResultList();
        if (rows.isEmpty()) throw new ResourceNotFoundException("Usuario no encontrado");
        return rows.get(0) == null ? BigDecimal.ZERO : new BigDecimal(rows.get(0).toString());
    }

    /** Bloquea la fila del jinete y devuelve su empleador_id actual (o null). */
    private Object bloquearJinete(Long jineteId) {
        List<?> rows = entityManager.createNativeQuery("SELECT empleador_id FROM jinetes WHERE id = :id FOR UPDATE")
                .setParameter("id", jineteId).getResultList();
        if (rows.isEmpty()) throw new ResourceNotFoundException("Jinete no encontrado");
        return rows.get(0);
    }

    /** Datos básicos del jinete (sin el empleador, para no forzar cargas perezosas). */
    public static Map<String, Object> resumen(Jinete j) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", j.getId());
        m.put("nombre", j.getNombre());
        m.put("habilidad", j.getHabilidad());
        m.put("peso", j.getPeso());
        m.put("experiencia", j.getExperiencia());
        m.put("especialidad", j.getEspecialidad());
        m.put("precio_contratacion", j.getPrecioContratacion());
        m.put("caballo_id", j.getCaballo() != null ? j.getCaballo().getId() : null);
        m.put("caballo_nombre", j.getCaballo() != null ? j.getCaballo().getNombre() : null);
        return m;
    }

    public static Map<String, Object> detalle(Jinete j) {
        Map<String, Object> m = resumen(j);
        m.put("disponible", j.getEmpleador() == null);
        m.put("fecha_contratacion", j.getFechaContratacion());
        if (j.getEmpleador() != null) {
            Map<String, Object> empleador = new HashMap<>();
            empleador.put("id", j.getEmpleador().getId());
            empleador.put("username", j.getEmpleador().getUsername());
            m.put("empleador", empleador);
        } else {
            m.put("empleador", null);
        }
        return m;
    }
}
