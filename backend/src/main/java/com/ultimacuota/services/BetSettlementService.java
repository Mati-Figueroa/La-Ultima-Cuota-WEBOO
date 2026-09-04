package com.ultimacuota.services;

import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BetSettlementService {

    private final ApuestaRepository apuestaRepository;
    private final CaballoRepository caballoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransaccionSaldoRepository transaccionRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final InscripcionRepository inscripcionRepository;

    @Transactional
    public void settleRace(Long raceId, List<Map<String, Object>> results) {
        Long winnerHorseId = (Long) results.get(0).get("caballo_id");
        List<Apuesta> pendingBets = apuestaRepository.findPendingBetsWithCarrera(raceId);

        BigDecimal totalPool = pendingBets.stream()
                .map(Apuesta::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal winnerPool = pendingBets.stream()
                .filter(b -> b.getCaballo().getId().equals(winnerHorseId))
                .map(Apuesta::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalHumanWinnings = BigDecimal.ZERO;

        for (Apuesta bet : pendingBets) {
            if (bet.getUsuario() == null) {
                String newStatus = bet.getCaballo().getId().equals(winnerHorseId) ? "ganada" : "perdida";
                bet.setEstado(newStatus);
                apuestaRepository.save(bet);
                continue;
            }

            if (bet.getCaballo().getId().equals(winnerHorseId)) {
                BigDecimal winAmount = winnerPool.compareTo(BigDecimal.ZERO) > 0
                        ? bet.getMonto().multiply(totalPool).divide(winnerPool, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                totalHumanWinnings = totalHumanWinnings.add(winAmount);
                bet.setEstado("ganada");
                bet.setMontoGanado(winAmount);
                apuestaRepository.save(bet);

                Usuario usuario = usuarioRepository.getReferenceById(bet.getUsuario().getId());
                usuario.setSaldo(usuario.getSaldo().add(winAmount));
                usuarioRepository.save(usuario);

                TransaccionSaldo tx = TransaccionSaldo.builder()
                        .usuario(usuario)
                        .tipo("apuesta_ganada")
                        .monto(winAmount)
                        .saldoResultante(usuario.getSaldo())
                        .referenciaTabla("carreras")
                        .referenciaId(raceId)
                        .build();
                transaccionRepository.save(tx);
            } else {
                bet.setEstado("perdida");
                apuestaRepository.save(bet);
            }
        }

        BigDecimal commissionPct = configuracionRepository.findByClave("owner_commission_pct")
                .map(c -> new BigDecimal(c.getValor()))
                .orElse(BigDecimal.valueOf(10));

        Inscripcion winnerInscription = inscripcionRepository
                .findByCarreraIdWithCaballo(raceId).stream()
                .filter(i -> i.getCaballo().getId().equals(winnerHorseId))
                .findFirst().orElse(null);

        if (winnerInscription != null && winnerInscription.getUsuario() != null) {
            BigDecimal commission = totalHumanWinnings.multiply(commissionPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (commission.compareTo(BigDecimal.ZERO) > 0) {
                Usuario owner = usuarioRepository.getReferenceById(winnerInscription.getUsuario().getId());
                owner.setSaldo(owner.getSaldo().add(commission));
                usuarioRepository.save(owner);

                TransaccionSaldo tx = TransaccionSaldo.builder()
                        .usuario(owner)
                        .tipo("comision_dueno")
                        .monto(commission)
                        .saldoResultante(owner.getSaldo())
                        .referenciaTabla("carreras")
                        .referenciaId(raceId)
                        .build();
                transaccionRepository.save(tx);
            }
        }

        for (Map<String, Object> r : results) {
            Long caballoId = (Long) r.get("caballo_id");
            int posicion = (int) r.get("posicion");
            int wins = posicion == 1 ? 1 : 0;
            caballoRepository.incrementStats(caballoId, wins, 1, posicion);
        }
    }
}
