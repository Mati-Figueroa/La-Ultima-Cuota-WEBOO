package com.ultimacuota.services;

import com.ultimacuota.dto.*;
import com.ultimacuota.exceptions.ConflictException;
import com.ultimacuota.exceptions.InsufficientBalanceException;
import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionService {

    private final SubastaRepository subastaRepository;
    private final PujaRepository pujaRepository;
    private final CaballoRepository caballoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransaccionSaldoRepository transaccionRepository;
    private final InscripcionRepository inscripcionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Map<String, Object>> getActiveAuctions(String search, String sort) {
        List<Subasta> auctions;
        if ("ending_soon".equals(sort)) {
            auctions = subastaRepository.findActiveAuctions().stream()
                    .filter(s -> s.getFechaFin().isAfter(LocalDateTime.now()))
                    .sorted(Comparator.comparing(Subasta::getFechaFin))
                    .collect(Collectors.toList());
        } else if ("newest".equals(sort)) {
            auctions = subastaRepository.findActiveAuctions().stream()
                    .sorted(Comparator.comparing(Subasta::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        } else if ("price_low".equals(sort)) {
            auctions = subastaRepository.findActiveAuctions().stream()
                    .sorted(Comparator.comparing(s -> getCurrentPrice(s).negate()))
                    .collect(Collectors.toList());
        } else {
            auctions = subastaRepository.findActiveAuctions();
        }

        if (search != null && !search.trim().isEmpty()) {
            String lowerSearch = search.trim().toLowerCase();
            auctions = auctions.stream()
                    .filter(s -> s.getCaballo().getNombre().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        }

        return auctions.stream().map(this::toAuctionMap).collect(Collectors.toList());
    }

    public Map<String, Object> getAuctionById(Long id) {
        Subasta auction = subastaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subasta no encontrada"));
        Map<String, Object> map = toAuctionMap(auction);

        List<Map<String, Object>> bids = pujaRepository.findBySubastaIdOrderByMontoDescFechaAsc(id)
                .stream().map(this::toBidMap).collect(Collectors.toList());
        map.put("pujas", bids);

        return map;
    }

    public List<Map<String, Object>> getMyAuctions(Long userId) {
        return subastaRepository.findByVendedorIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toAuctionMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getMyBids(Long userId) {
        return pujaRepository.findByUsuarioIdOrderByFechaDesc(userId)
                .stream().map(this::toBidMap).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createAuction(CreateAuctionRequest request, Long userId) {
        if (request.getCaballoId() == null || request.getPrecioInicial() == null || request.getDuracionHoras() == null) {
            throw new IllegalArgumentException("Todos los campos son obligatorios");
        }
        if (request.getPrecioInicial().compareTo(BigDecimal.valueOf(100)) < 0) {
            throw new IllegalArgumentException("El precio inicial debe ser al menos $100 CC");
        }
        if (request.getDuracionHoras() < 1 || request.getDuracionHoras() > 168) {
            throw new IllegalArgumentException("La duración debe ser entre 1 y 168 horas");
        }

        Caballo horse = caballoRepository.findById(request.getCaballoId())
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado"));

        if (horse.getPropietario() == null || !horse.getPropietario().getId().equals(userId)) {
            throw new ResourceNotFoundException("Caballo no encontrado en tu establo");
        }
        if (horse.getEsBot()) {
            throw new IllegalArgumentException("Los caballos bot no se pueden subastar");
        }
        if (horse.getEnVenta()) {
            throw new IllegalArgumentException("Este caballo ya está en venta directa. Quítalo de la venta primero.");
        }
        if (subastaRepository.isHorseInActiveAuction(horse.getId())) {
            throw new IllegalArgumentException("Este caballo ya tiene una subasta activa");
        }
        if (inscripcionRepository.isInscribedInActiveRace(horse.getId())) {
            throw new IllegalArgumentException("No puedes subastar un caballo inscrito en una carrera activa");
        }

        Usuario seller = usuarioRepository.getReferenceById(userId);
        Subasta auction = Subasta.builder()
                .caballo(horse)
                .vendedor(seller)
                .precioInicial(request.getPrecioInicial())
                .precioReserva(request.getPrecioReserva())
                .fechaInicio(LocalDateTime.now())
                .fechaFin(LocalDateTime.now().plusHours(request.getDuracionHoras()))
                .estado("activa")
                .build();
        auction = subastaRepository.save(auction);

        log.info("[Auction] Subasta #{} creada por usuario {} para caballo {} ({}h)",
                auction.getId(), userId, horse.getId(), request.getDuracionHoras());

        Map<String, Object> result = new HashMap<>();
        result.put("subasta", toAuctionMap(auction));
        return result;
    }

    @Transactional
    public Map<String, Object> placeBid(Long auctionId, BidRequest request, Long userId) {
        if (request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        Subasta auction = subastaRepository.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subasta no encontrada"));

        if (!"activa".equals(auction.getEstado())) {
            throw new IllegalArgumentException("Esta subasta ya no está activa");
        }
        if (LocalDateTime.now().isAfter(auction.getFechaFin())) {
            throw new IllegalArgumentException("Esta subasta ya expiró");
        }
        if (auction.getVendedor().getId().equals(userId)) {
            throw new IllegalArgumentException("No puedes pujar en tu propia subasta");
        }

        BigDecimal currentPrice = getCurrentPrice(auction);
        if (request.getMonto().compareTo(currentPrice) <= 0) {
            throw new IllegalArgumentException(
                    String.format("Tu puja debe ser mayor al precio actual ($%,.0f CC)", currentPrice));
        }

        Query saldoQuery = entityManager.createNativeQuery(
                "SELECT saldo FROM usuarios WHERE id = :id FOR UPDATE");
        saldoQuery.setParameter("id", userId);
        Object saldoResult = saldoQuery.getSingleResult();
        BigDecimal userSaldo = new BigDecimal(saldoResult.toString());

        if (userSaldo.compareTo(request.getMonto()) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Saldo insuficiente. Necesitas $%,.0f CC, tienes $%,.0f CC",
                            request.getMonto(), userSaldo));
        }

        // Refund previous bidder if exists
        Optional<Puja> existingBidOpt = pujaRepository.findBySubastaIdAndUsuarioIdOrderByMontoDesc(auctionId, userId);
        if (existingBidOpt.isPresent()) {
            Puja existingBid = existingBidOpt.get();
            // Refund the previous bid
            Usuario prevBidder = usuarioRepository.getReferenceById(userId);
            prevBidder.setSaldo(prevBidder.getSaldo().add(existingBid.getMonto()));
            usuarioRepository.save(prevBidder);

            TransaccionSaldo refundTx = TransaccionSaldo.builder()
                    .usuario(prevBidder)
                    .tipo("ajuste_admin")
                    .monto(existingBid.getMonto())
                    .saldoResultante(prevBidder.getSaldo())
                    .referenciaTabla("subastas")
                    .referenciaId(auctionId)
                    .build();
            transaccionRepository.save(refundTx);

            // Refund ALL other outbid users
            List<Puja> allBids = pujaRepository.findBySubastaIdOrderByMontoDescFechaAsc(auctionId);
            for (Puja bid : allBids) {
                if (!bid.getUsuario().getId().equals(userId) && bid.getMonto().compareTo(request.getMonto()) < 0) {
                    Usuario outbidUser = usuarioRepository.getReferenceById(bid.getUsuario().getId());
                    outbidUser.setSaldo(outbidUser.getSaldo().add(bid.getMonto()));
                    usuarioRepository.save(outbidUser);

                    TransaccionSaldo outbidRefund = TransaccionSaldo.builder()
                            .usuario(outbidUser)
                            .tipo("ajuste_admin")
                            .monto(bid.getMonto())
                            .saldoResultante(outbidUser.getSaldo())
                            .referenciaTabla("subastas")
                            .referenciaId(auctionId)
                            .build();
                    transaccionRepository.save(outbidRefund);
                }
            }
        }

        // Deduct new bid amount
        Usuario bidder = usuarioRepository.getReferenceById(userId);
        bidder.setSaldo(bidder.getSaldo().subtract(request.getMonto()));
        usuarioRepository.save(bidder);

        // Delete previous bid from this user (unique constraint: subasta_id + usuario_id)
        existingBidOpt.ifPresent(pujaRepository::delete);

        // Create new bid
        Puja bid = Puja.builder()
                .subasta(auction)
                .usuario(bidder)
                .monto(request.getMonto())
                .fecha(LocalDateTime.now())
                .build();
        bid = pujaRepository.save(bid);

        TransaccionSaldo bidTx = TransaccionSaldo.builder()
                .usuario(bidder)
                .tipo("apuesta_realizada")
                .monto(request.getMonto().negate())
                .saldoResultante(bidder.getSaldo())
                .referenciaTabla("subastas")
                .referenciaId(auctionId)
                .build();
        transaccionRepository.save(bidTx);

        Map<String, Object> result = new HashMap<>();
        result.put("puja", toBidMap(bid));
        result.put("saldo", bidder.getSaldo());
        result.put("precio_actual", request.getMonto());
        return result;
    }

    @Transactional
    public void finalizeAuction(Subasta auction) {
        Optional<Puja> highestBidOpt = pujaRepository.findHighestBid(auction.getId());

        if (highestBidOpt.isEmpty()) {
            // No bids - just close the auction
            subastaRepository.updateEstado(auction.getId(), "finalizada");
            log.info("[Auction] Subasta #{} finalizada sin pujas", auction.getId());
            return;
        }

        Puja highestBid = highestBidOpt.get();
        BigDecimal winningAmount = highestBid.getMonto();

        // Check reserve price
        if (auction.getPrecioReserva() != null && winningAmount.compareTo(auction.getPrecioReserva()) < 0) {
            // Reserve not met - refund winner, cancel auction
            Usuario winner = usuarioRepository.getReferenceById(highestBid.getUsuario().getId());
            winner.setSaldo(winner.getSaldo().add(winningAmount));
            usuarioRepository.save(winner);

            TransaccionSaldo refundTx = TransaccionSaldo.builder()
                    .usuario(winner)
                    .tipo("ajuste_admin")
                    .monto(winningAmount)
                    .saldoResultante(winner.getSaldo())
                    .referenciaTabla("subastas")
                    .referenciaId(auction.getId())
                    .build();
            transaccionRepository.save(refundTx);

            subastaRepository.updateEstado(auction.getId(), "cancelada");
            log.info("[Auction] Subasta #{} cancelada - reserva no alcanzada", auction.getId());
            return;
        }

        // Transfer horse to winner
        caballoRepository.transfer(auction.getCaballo().getId(), highestBid.getUsuario().getId());

        // Mark winning bid
        pujaRepository.clearWinners(auction.getId());
        pujaRepository.markAsWinner(highestBid.getId());

        // Finalize auction
        subastaRepository.finalizeWithWinner(auction.getId(), highestBid.getUsuario().getId());

        log.info("[Auction] Subasta #{} finalizada - ganador: {} (${},.0f CC)",
                auction.getId(), highestBid.getUsuario().getUsername(), winningAmount);
    }

    public BigDecimal getCurrentPrice(Subasta auction) {
        Optional<Puja> highestBid = pujaRepository.findHighestBid(auction.getId());
        return highestBid.map(Puja::getMonto).orElse(auction.getPrecioInicial());
    }

    private Map<String, Object> toAuctionMap(Subasta s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("caballo_id", s.getCaballo().getId());
        map.put("caballo_nombre", s.getCaballo().getNombre());
        map.put("caballo_edad", s.getCaballo().getEdad());
        map.put("caballo_velocidad", s.getCaballo().getVelocidad());
        map.put("caballo_resistencia", s.getCaballo().getResistencia());
        map.put("caballo_corazon", s.getCaballo().getCorazon());
        map.put("caballo_fatiga", s.getCaballo().getFatiga());
        map.put("caballo_carreras", s.getCaballo().getCarrerasTotales());
        map.put("caballo_victorias", s.getCaballo().getVictorias());
        map.put("vendedor_id", s.getVendedor().getId());
        map.put("vendedor_username", s.getVendedor().getUsername());
        map.put("precio_inicial", s.getPrecioInicial());
        map.put("precio_reserva", s.getPrecioReserva());
        map.put("precio_actual", getCurrentPrice(s));
        map.put("fecha_inicio", s.getFechaInicio());
        map.put("fecha_fin", s.getFechaFin());
        map.put("estado", s.getEstado());
        map.put("total_pujas", pujaRepository.countBidsByAuction(s.getId()));
        if (s.getGanador() != null) {
            map.put("ganador_id", s.getGanador().getId());
            map.put("ganador_username", s.getGanador().getUsername());
        }
        map.put("created_at", s.getCreatedAt());
        return map;
    }

    private Map<String, Object> toBidMap(Puja p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("subasta_id", p.getSubasta().getId());
        map.put("usuario_id", p.getUsuario().getId());
        map.put("usuario_username", p.getUsuario().getUsername());
        map.put("monto", p.getMonto());
        map.put("fecha", p.getFecha());
        map.put("es_ganadora", p.getEsGanadora());
        return map;
    }
}
