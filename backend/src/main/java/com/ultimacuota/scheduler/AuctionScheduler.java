package com.ultimacuota.scheduler;

import com.ultimacuota.models.Subasta;
import com.ultimacuota.repositories.SubastaRepository;
import com.ultimacuota.services.AuctionService;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionScheduler {

    private final SubastaRepository subastaRepository;
    private final AuctionService auctionService;

    private SocketIOServer socketIOServer;

    public void setSocketIOServer(SocketIOServer server) {
        this.socketIOServer = server;
    }

    @PostConstruct
    public void init() {
        auctionService.healIncompleteAuctions();
    }

    @Scheduled(fixedRate = 1000, initialDelay = 2000)
    @Transactional
    public void checkExpiredAuctions() {
        try {
            List<Subasta> expired = subastaRepository.findExpiredAuctions(LocalDateTime.now());
            for (Subasta auction : expired) {
                auctionService.finalizeAuction(auction);

                // Broadcast auction result via Socket.IO
                if (socketIOServer != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("subasta_id", auction.getId());
                    data.put("estado", "finalizada");
                    socketIOServer.getBroadcastOperations().sendEvent("auction_ended", data);
                }
            }
            if (!expired.isEmpty()) {
                log.info("[AuctionScheduler] {} subastas expiradas procesadas", expired.size());
            }
        } catch (Exception e) {
            log.error("[AuctionScheduler] Error procesando subastas: {}", e.getMessage());
        }
    }
}
