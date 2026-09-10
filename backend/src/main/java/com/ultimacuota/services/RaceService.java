package com.ultimacuota.services;

import com.ultimacuota.dto.BetRequest;
import com.ultimacuota.dto.InscribeRequest;
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
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RaceService {

    private final CarreraRepository carreraRepository;
    private final InscripcionRepository inscripcionRepository;
    private final CaballoRepository caballoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApuestaRepository apuestaRepository;
    private final TransaccionSaldoRepository transaccionRepository;
    private final ResultadoCarreraRepository resultadoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Map<String, Object>> getAll(String estado) {
        List<Carrera> carreras = carreraRepository.findAllByEstado(estado);
        return carreras.stream().map(this::toRaceSummary).collect(Collectors.toList());
    }

    public Map<String, Object> getById(Long id) {
        Carrera carrera = carreraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada"));

        Map<String, Object> result = toRaceSummary(carrera);
        List<Map<String, Object>> inscripciones = inscripcionRepository.findByCarreraIdWithCaballo(id)
                .stream().map(this::toInscriptionMap).collect(Collectors.toList());
        result.put("inscripciones", inscripciones);
        return result;
    }

    public Map<String, Object> getOdds(Long id) {
        List<Apuesta> pendingBets = apuestaRepository.findPendingByCarreraId(id);
        List<Inscripcion> inscripciones = inscripcionRepository.findByCarreraIdWithCaballo(id);

        BigDecimal totalPool = pendingBets.stream()
                .map(Apuesta::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, BigDecimal> horsePoolMap = new HashMap<>();
        for (Apuesta bet : pendingBets) {
            horsePoolMap.merge(bet.getCaballo().getId(), bet.getMonto(), BigDecimal::add);
        }

        List<Map<String, Object>> odds = inscripciones.stream().map(insc -> {
            Map<String, Object> o = new HashMap<>();
            o.put("caballo_id", insc.getCaballo().getId());
            o.put("nombre", insc.getCaballo().getNombre());
            o.put("total_apuestas", horsePoolMap.getOrDefault(insc.getCaballo().getId(), BigDecimal.ZERO));

            BigDecimal horsePool = horsePoolMap.getOrDefault(insc.getCaballo().getId(), BigDecimal.ZERO);
            BigDecimal cuota = horsePool.compareTo(BigDecimal.ZERO) > 0
                    ? totalPool.divide(horsePool, 2, RoundingMode.HALF_UP)
                    : new BigDecimal("1.00");
            o.put("cuota", cuota);
            return o;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("odds", odds);
        result.put("pool_total", totalPool);
        return result;
    }

    public List<Map<String, Object>> getResults(Long id) {
        Carrera carrera = carreraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada"));
        if (!"finalizada".equals(carrera.getEstado())) {
            throw new IllegalArgumentException("La carrera aún no ha finalizado");
        }
        return resultadoRepository.findByCarreraIdOrderByPosicionFinalAsc(id)
                .stream().map(this::toResultMap).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> inscribe(Long raceId, InscribeRequest request, Long userId) {
        if (request == null || request.getCaballoId() == null) {
            throw new IllegalArgumentException("Debe seleccionar un caballo válido");
        }

        Carrera carrera = carreraRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada"));

        if (!"programada".equals(carrera.getEstado())) {
            throw new IllegalArgumentException("Solo puedes inscribirte en carreras programadas");
        }

        long count = inscripcionRepository.countByCarreraId(raceId);
        if (count >= carrera.getCupoMaximo()) {
            throw new IllegalArgumentException("La carrera está llena");
        }

        Caballo horse = caballoRepository.findById(request.getCaballoId())
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado en tu establo"));

        if (horse.getPropietario() == null || !horse.getPropietario().getId().equals(userId)) {
            throw new ResourceNotFoundException("Caballo no encontrado en tu establo");
        }

        if (horse.getFatiga() >= 80) {
            throw new IllegalArgumentException("El caballo está demasiado fatigado para competir");
        }

        if (inscripcionRepository.existsByCarreraIdAndCaballoId(raceId, request.getCaballoId())) {
            throw new IllegalArgumentException("Este caballo ya está inscrito en esta carrera");
        }

        int numeroCarril = inscripcionRepository.findNextLane(raceId);

        Usuario usuario = usuarioRepository.getReferenceById(userId);
        Inscripcion inscripcion = Inscripcion.builder()
                .carrera(carrera)
                .caballo(horse)
                .usuario(usuario)
                .numeroCarril(numeroCarril)
                .build();
        inscripcionRepository.save(inscripcion);

        carrera.setTieneInteraccionHumana(true);
        carreraRepository.save(carrera);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Inscripción exitosa");
        result.put("numero_carril", numeroCarril);
        return result;
    }

    @Transactional
    public Map<String, Object> placeBet(Long raceId, BetRequest request, Long userId) {
        if (request.getCaballoId() == null || request.getMonto() == null || request.getMonto().doubleValue() <= 0) {
            throw new IllegalArgumentException("Datos de apuesta inválidos");
        }

        Carrera carrera = carreraRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada"));

        if (!"programada".equals(carrera.getEstado())) {
            throw new IllegalArgumentException("Solo puedes apostar en carreras programadas");
        }

        if (!inscripcionRepository.existsByCarreraIdAndCaballoId(raceId, request.getCaballoId())) {
            throw new IllegalArgumentException("El caballo no está inscrito en esta carrera");
        }

        BigDecimal monto = new BigDecimal(request.getMonto().toString());

        Query saldoQuery = entityManager.createNativeQuery(
                "SELECT saldo FROM usuarios WHERE id = :id FOR UPDATE");
        saldoQuery.setParameter("id", userId);
        Object saldoResult = saldoQuery.getSingleResult();
        BigDecimal userSaldo = new BigDecimal(saldoResult.toString());

        if (userSaldo.compareTo(monto) < 0) {
            throw new InsufficientBalanceException("Saldo insuficiente para esta apuesta");
        }

        List<Apuesta> pendingBets = apuestaRepository.findPendingByCarreraId(raceId);
        Map<Long, BigDecimal> betTotals = new HashMap<>();
        for (Apuesta bet : pendingBets) {
            betTotals.merge(bet.getCaballo().getId(), bet.getMonto(), BigDecimal::add);
        }
        betTotals.merge(request.getCaballoId(), monto, BigDecimal::add);

        BigDecimal poolTotal = betTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal horsePool = betTotals.getOrDefault(request.getCaballoId(), BigDecimal.ZERO);
        BigDecimal cuota = horsePool.compareTo(BigDecimal.ZERO) > 0
                ? poolTotal.divide(horsePool, 2, RoundingMode.HALF_UP)
                : new BigDecimal("1.00");

        Usuario usuario = usuarioRepository.getReferenceById(userId);
        usuario.setSaldo(usuario.getSaldo().subtract(monto));
        usuarioRepository.save(usuario);

        Caballo caballo = caballoRepository.getReferenceById(request.getCaballoId());
        Apuesta apuesta = Apuesta.builder()
                .usuario(usuario)
                .carrera(carrera)
                .caballo(caballo)
                .monto(monto)
                .cuota(cuota)
                .estado("pendiente")
                .build();
        apuestaRepository.save(apuesta);

        TransaccionSaldo transaccion = TransaccionSaldo.builder()
                .usuario(usuario)
                .tipo("apuesta_realizada")
                .monto(monto.negate())
                .saldoResultante(usuario.getSaldo())
                .referenciaTabla("carreras")
                .referenciaId(raceId)
                .build();
        transaccionRepository.save(transaccion);

        carrera.setTieneInteraccionHumana(true);
        carreraRepository.save(carrera);

        Map<String, Object> result = new HashMap<>();
        result.put("cuota", cuota);
        result.put("monto", monto);
        result.put("saldo", usuario.getSaldo());
        return result;
    }

    private Map<String, Object> toRaceSummary(Carrera c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("nombre", c.getNombre());
        map.put("estado", c.getEstado());
        map.put("fecha_programada", c.getFechaProgramada());
        map.put("fecha_inicio_real", c.getFechaInicioReal());
        map.put("fecha_fin_real", c.getFechaFinReal());
        map.put("cupo_maximo", c.getCupoMaximo());
        map.put("tiene_interaccion_humana", c.getTieneInteraccionHumana());
        map.put("created_at", c.getCreatedAt());
        map.put("participantes_actuales", inscripcionRepository.countByCarreraId(c.getId()));
        return map;
    }

    private Map<String, Object> toInscriptionMap(Inscripcion i) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", i.getId());
        map.put("carrera_id", i.getCarrera().getId());
        map.put("caballo_id", i.getCaballo().getId());
        map.put("caballo_nombre", i.getCaballo().getNombre());
        map.put("edad", i.getCaballo().getEdad());
        map.put("fatiga", i.getCaballo().getFatiga());
        map.put("carreras_totales", i.getCaballo().getCarrerasTotales());
        map.put("es_bot", i.getCaballo().getEsBot());
        map.put("numero_carril", i.getNumeroCarril());
        map.put("fecha_inscripcion", i.getFechaInscripcion());
        map.put("dueno_username", i.getUsuario() != null ? i.getUsuario().getUsername() : null);
        return map;
    }

    private Map<String, Object> toResultMap(ResultadoCarrera r) {
        Map<String, Object> map = new HashMap<>();
        map.put("posicion_final", r.getPosicionFinal());
        map.put("tiempo_final", r.getTiempoFinal());
        map.put("caballo_id", r.getCaballo().getId());
        map.put("caballo_nombre", r.getCaballo().getNombre());
        map.put("edad", r.getCaballo().getEdad());
        map.put("fatiga", r.getCaballo().getFatiga());
        map.put("carreras_totales", r.getCaballo().getCarrerasTotales());
        map.put("victorias", r.getCaballo().getVictorias());
        map.put("dueno_username", r.getCaballo().getPropietario() != null
                ? r.getCaballo().getPropietario().getUsername() : null);
        return map;
    }
}
