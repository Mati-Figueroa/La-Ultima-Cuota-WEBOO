package com.ultimacuota.services;

import com.ultimacuota.dto.*;
import com.ultimacuota.exceptions.ConflictException;
import com.ultimacuota.exceptions.InsufficientBalanceException;
import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import com.corundumstudio.socketio.SocketIOServer;
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

    private SocketIOServer socketIOServer;

    public void setSocketIOServer(SocketIOServer server) {
        this.socketIOServer = server;
    }

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

        // Previous highest bid before this placement
        Optional<Puja> previousHighestBidOpt = pujaRepository.findHighestBid(auctionId);

        // Lock user balance check
        Query saldoQuery = entityManager.createNativeQuery(
                "SELECT saldo FROM usuarios WHERE id = :id FOR UPDATE");
        saldoQuery.setParameter("id", userId);
        Object saldoResult = saldoQuery.getSingleResult();
        BigDecimal currentSaldo = new BigDecimal(saldoResult.toString());

        Optional<Puja> userExistingBidOpt = pujaRepository.findBySubastaIdAndUsuarioIdOrderByMontoDesc(auctionId, userId);
        BigDecimal previousUserBidAmount = userExistingBidOpt.map(Puja::getMonto).orElse(BigDecimal.ZERO);
        BigDecimal effectiveUserSaldo = currentSaldo.add(previousUserBidAmount);

        if (effectiveUserSaldo.compareTo(request.getMonto()) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Saldo insuficiente. Necesitas $%,.0f CC, tienes $%,.0f CC",
                            request.getMonto(), currentSaldo));
        }

        // Refund previous outbid highest bidder if it was another user
        if (previousHighestBidOpt.isPresent()) {
            Puja prevHighestBid = previousHighestBidOpt.get();
            if (!prevHighestBid.getUsuario().getId().equals(userId)) {
                Usuario outbidUser = usuarioRepository.findById(prevHighestBid.getUsuario().getId()).orElse(null);
                if (outbidUser != null) {
                    outbidUser.setSaldo(outbidUser.getSaldo().add(prevHighestBid.getMonto()));
                    usuarioRepository.save(outbidUser);

                    TransaccionSaldo outbidRefund = TransaccionSaldo.builder()
                            .usuario(outbidUser)
                            .tipo("ajuste_admin")
                            .monto(prevHighestBid.getMonto())
                            .saldoResultante(outbidUser.getSaldo())
                            .referenciaTabla("subastas")
                            .referenciaId(auctionId)
                            .build();
                    transaccionRepository.save(outbidRefund);

                    log.info("[Auction] Usuario {} superó la puja de usuario {}. Reembolsados ${} a usuario {}",
                            userId, outbidUser.getId(), prevHighestBid.getMonto(), outbidUser.getId());
                }
            }
        }

        // Deduct balance for bidder
        Usuario bidder = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        BigDecimal newSaldo = effectiveUserSaldo.subtract(request.getMonto());
        bidder.setSaldo(newSaldo);
        usuarioRepository.save(bidder);

        BigDecimal deltaDeduction = request.getMonto().subtract(previousUserBidAmount);
        TransaccionSaldo bidTx = TransaccionSaldo.builder()
                .usuario(bidder)
                .tipo("puja_subasta")
                .monto(deltaDeduction.negate())
                .saldoResultante(newSaldo)
                .referenciaTabla("subastas")
                .referenciaId(auctionId)
                .build();
        transaccionRepository.save(bidTx);

        // Update or insert Puja record safely without Hibernate unique key collision
        Puja bid;
        if (userExistingBidOpt.isPresent()) {
            bid = userExistingBidOpt.get();
            bid.setMonto(request.getMonto());
            bid.setFecha(LocalDateTime.now());
            bid = pujaRepository.saveAndFlush(bid);
        } else {
            bid = Puja.builder()
                    .subasta(auction)
                    .usuario(bidder)
                    .monto(request.getMonto())
                    .fecha(LocalDateTime.now())
                    .build();
            bid = pujaRepository.saveAndFlush(bid);
        }

        boolean reserveReached = auction.getPrecioReserva() != null &&
                request.getMonto().compareTo(auction.getPrecioReserva()) >= 0;

        if (reserveReached) {
            log.info("[Auction] Subasta #{} alcanzó el precio de reserva con puja ${}. Finalizando transacción...",
                    auctionId, request.getMonto());
            finalizeAuction(auction);
        }

        if (socketIOServer != null) {
            Map<String, Object> bidEvt = new HashMap<>();
            bidEvt.put("subasta_id", auctionId);
            bidEvt.put("puja", toBidMap(bid));
            bidEvt.put("precio_actual", request.getMonto());
            bidEvt.put("finalizada", reserveReached);
            socketIOServer.getRoomOperations("auction_" + auctionId).sendEvent("new_bid", bidEvt);
            socketIOServer.getRoomOperations("auction_" + auctionId).sendEvent("auction_bid", bidEvt);
            socketIOServer.getBroadcastOperations().sendEvent("new_bid", bidEvt);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("puja", toBidMap(bid));
        result.put("saldo", bidder.getSaldo());
        result.put("precio_actual", request.getMonto());
        result.put("finalizada", reserveReached);
        return result;
    }

    @Transactional
    public void finalizeAuction(Subasta auction) {
        if ("finalizada".equals(auction.getEstado()) || "cancelada".equals(auction.getEstado())) {
            return;
        }

        Optional<Puja> highestBidOpt = pujaRepository.findHighestBid(auction.getId());

        if (highestBidOpt.isEmpty()) {
            subastaRepository.updateEstado(auction.getId(), "finalizada");
            if (socketIOServer != null) {
                Map<String, Object> endEvt = new HashMap<>();
                endEvt.put("subasta_id", auction.getId());
                endEvt.put("estado", "finalizada");
                socketIOServer.getRoomOperations("auction_" + auction.getId()).sendEvent("auction_ended", endEvt);
                socketIOServer.getBroadcastOperations().sendEvent("auction_ended", endEvt);
            }
            log.info("[Auction] Subasta #{} finalizada sin pujas", auction.getId());
            return;
        }

        Puja highestBid = highestBidOpt.get();
        BigDecimal winningAmount = highestBid.getMonto();

        if (auction.getPrecioReserva() != null && winningAmount.compareTo(auction.getPrecioReserva()) < 0) {
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
            if (socketIOServer != null) {
                Map<String, Object> endEvt = new HashMap<>();
                endEvt.put("subasta_id", auction.getId());
                endEvt.put("estado", "cancelada");
                socketIOServer.getRoomOperations("auction_" + auction.getId()).sendEvent("auction_ended", endEvt);
                socketIOServer.getBroadcastOperations().sendEvent("auction_ended", endEvt);
            }
            log.info("[Auction] Subasta #{} cancelada - reserva no alcanzada", auction.getId());
            return;
        }

        Usuario winner = highestBid.getUsuario();

        // Transfer horse to winner and remove from market/auction
        Caballo horse = caballoRepository.findById(auction.getCaballo().getId()).orElse(null);
        if (horse != null) {
            horse.setPropietario(winner);
            horse.setEnVenta(false);
            horse.setPrecioVenta(null);
            caballoRepository.save(horse);
            log.info("[Auction] Caballo #{} transferido a ganador {}", horse.getId(), winner.getUsername());
        }

        // Credit coins to seller
        Usuario seller = usuarioRepository.findById(auction.getVendedor().getId()).orElse(null);
        if (seller != null) {
            seller.setSaldo(seller.getSaldo().add(winningAmount));
            usuarioRepository.save(seller);

            TransaccionSaldo sellerTx = TransaccionSaldo.builder()
                    .usuario(seller)
                    .tipo("venta_caballo")
                    .monto(winningAmount)
                    .saldoResultante(seller.getSaldo())
                    .referenciaTabla("subastas")
                    .referenciaId(auction.getId())
                    .build();
            transaccionRepository.save(sellerTx);
            log.info("[Auction] Vendedor {} acreditado con ${} CC", seller.getUsername(), winningAmount);
        }

        // Mark winning bid
        pujaRepository.clearWinners(auction.getId());
        pujaRepository.markAsWinner(highestBid.getId());

        // Finalize auction with winner
        auction.setEstado("finalizada");
        auction.setGanador(winner);
        subastaRepository.save(auction);

        if (socketIOServer != null) {
            Map<String, Object> endEvt = new HashMap<>();
            endEvt.put("subasta_id", auction.getId());
            endEvt.put("estado", "finalizada");
            endEvt.put("ganador_id", winner.getId());
            endEvt.put("ganador_username", winner.getUsername());
            socketIOServer.getRoomOperations("auction_" + auction.getId()).sendEvent("auction_ended", endEvt);
            socketIOServer.getBroadcastOperations().sendEvent("auction_ended", endEvt);
        }

        log.info("[Auction] Subasta #{} finalizada - vendedor acreditado: {} (${}), ganador: {}",
                auction.getId(), seller != null ? seller.getUsername() : "N/A", winningAmount, winner.getUsername());
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
        double wr = s.getCaballo().getCarrerasTotales() != null && s.getCaballo().getCarrerasTotales() > 0
                ? Math.round((double) s.getCaballo().getVictorias() / s.getCaballo().getCarrerasTotales() * 100.0)
                : 0.0;
        map.put("caballo_winrate", wr);
        map.put("caballo_fatiga", s.getCaballo().getFatiga());
        map.put("caballo_carreras", s.getCaballo().getCarrerasTotales());
        map.put("caballo_victorias", s.getCaballo().getVictorias());
        map.put("vendedor_id", s.getVendedor().getId());
        map.put("vendedor_username", s.getVendedor().getUsername());
        map.put("vendedor_photo", s.getVendedor().getProfilePhoto());
        map.put("vendedor_profile_photo", s.getVendedor().getProfilePhoto());
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
        map.put("usuario_photo", p.getUsuario().getProfilePhoto());
        map.put("usuario_profile_photo", p.getUsuario().getProfilePhoto());
        map.put("monto", p.getMonto());
        map.put("fecha", p.getFecha());
        map.put("es_ganadora", p.getEsGanadora());
        return map;
    }

    @Transactional
    public void healIncompleteAuctions() {
        try {
            List<Subasta> auctions = subastaRepository.findAll();
            for (Subasta s : auctions) {
                if ("finalizada".equals(s.getEstado()) && s.getGanador() == null) {
                    Optional<Puja> highestBidOpt = pujaRepository.findHighestBid(s.getId());
                    if (highestBidOpt.isPresent()) {
                        Puja highestBid = highestBidOpt.get();
                        if (s.getPrecioReserva() == null || highestBid.getMonto().compareTo(s.getPrecioReserva()) >= 0) {
                            Usuario winner = highestBid.getUsuario();
                            Caballo horse = s.getCaballo();
                            if (horse != null && !winner.getId().equals(horse.getPropietario() != null ? horse.getPropietario().getId() : null)) {
                                horse.setPropietario(winner);
                                horse.setEnVenta(false);
                                horse.setPrecioVenta(null);
                                caballoRepository.save(horse);
                                log.info("[AuctionHealing] Caballo #{} transferido a ganador {}", horse.getId(), winner.getUsername());
                            }
                            Usuario seller = s.getVendedor();
                            if (seller != null) {
                                seller.setSaldo(seller.getSaldo().add(highestBid.getMonto()));
                                usuarioRepository.save(seller);
                            }
                            highestBid.setEsGanadora(true);
                            pujaRepository.save(highestBid);
                            s.setGanador(winner);
                            subastaRepository.save(s);
                            log.info("[AuctionHealing] Reparada subasta #{} asignando ganador {}", s.getId(), winner.getUsername());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[AuctionHealing] Error al reparar subastas: {}", e.getMessage());
        }
    }
}
