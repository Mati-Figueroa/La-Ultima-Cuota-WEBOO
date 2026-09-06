package com.ultimacuota.config;

import com.corundumstudio.socketio.SocketIOServer;
import com.ultimacuota.scheduler.AuctionScheduler;
import com.ultimacuota.scheduler.RaceLifecycleManager;
import com.ultimacuota.services.RaceSimulationService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Profile;

import java.util.Map;

@Configuration
@Slf4j
@Profile("!test")
public class SocketIOConfig {

    @Value("${socket.io.port:4000}")
    private int port;

    private SocketIOServer server;

    @Bean
    public SocketIOServer socketIOServer(RaceLifecycleManager raceLifecycleManager, RaceSimulationService simulationService, AuctionScheduler auctionScheduler) {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setPort(port);
        config.setOrigin("http://localhost:3000");
        config.setAllowCustomRequests(true);

        server = new SocketIOServer(config);

        server.addConnectListener(client -> {
            log.info("[Socket] Cliente conectado: {}", client.getSessionId());
        });

        server.addDisconnectListener(client -> {
            log.info("[Socket] Cliente desconectado: {}", client.getSessionId());
        });

        server.addEventListener("join_race", Map.class, (client, data, ackSender) -> {
            Number raceIdNum = (Number) data.get("carrera_id");
            if (raceIdNum == null) return;
            long raceId = raceIdNum.longValue();
            client.joinRoom("race_" + raceId);

            Map<Long, Double> positions = simulationService.getPositions(raceId);
            if (positions != null) {
                Map<String, Object> posData = new java.util.HashMap<>();
                posData.put("carrera_id", raceId);
                posData.put("positions", positions);
                posData.put("elapsed", 0);
                client.sendEvent("race_positions", posData);
            }
        });

        server.addEventListener("leave_race", Map.class, (client, data, ackSender) -> {
            Number raceIdNum = (Number) data.get("carrera_id");
            if (raceIdNum == null) return;
            client.leaveRoom("race_" + raceIdNum.longValue());
        });

        server.addEventListener("join_auction", Map.class, (client, data, ackSender) -> {
            Number auctionIdNum = (Number) data.get("subasta_id");
            if (auctionIdNum == null) return;
            client.joinRoom("auction_" + auctionIdNum);
        });

        server.addEventListener("leave_auction", Map.class, (client, data, ackSender) -> {
            Number auctionIdNum = (Number) data.get("subasta_id");
            if (auctionIdNum == null) return;
            client.leaveRoom("auction_" + auctionIdNum);
        });

        server.start();
        raceLifecycleManager.setSocketIOServer(server);
        auctionScheduler.setSocketIOServer(server);
        log.info("[Socket.IO] Servidor iniciado en puerto {}", port);

        return server;
    }

    @PreDestroy
    public void shutdown() {
        if (server != null) {
            server.stop();
            log.info("[Socket.IO] Servidor detenido");
        }
    }
}
