package com.ultimacuota.services;

import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DailyService {

    private static final long HOURS_24_SECONDS = 24 * 60 * 60;

    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final TransaccionSaldoRepository transaccionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Map<String, Object> claimDaily(Long userId) {
        Usuario usuario = usuarioRepository.findById(userId).orElseThrow();

        if (usuario.getUltimaRecompensaDiaria() != null) {
            LocalDateTime lastClaim = usuario.getUltimaRecompensaDiaria();
            long diffSeconds = Duration.between(lastClaim, LocalDateTime.now()).getSeconds();
            if (diffSeconds < HOURS_24_SECONDS) {
                Map<String, Object> result = new HashMap<>();
                result.put("retryAfter", HOURS_24_SECONDS - diffSeconds);
                throw new com.ultimacuota.exceptions.ConflictException("Debes esperar 24 horas entre reclamos");
            }
        }

        BigDecimal amount = configuracionRepository.findByClave("daily_reward_amount")
                .map(c -> new BigDecimal(c.getValor()))
                .orElse(BigDecimal.valueOf(500));

        BigDecimal currentSaldo = usuario.getSaldo() != null ? usuario.getSaldo() : BigDecimal.ZERO;
        usuario.setSaldo(currentSaldo.add(amount));
        usuario.setUltimaRecompensaDiaria(LocalDateTime.now());
        usuarioRepository.save(usuario);

        TransaccionSaldo tx = TransaccionSaldo.builder()
                .usuario(usuario)
                .tipo("moneda_diaria")
                .monto(amount)
                .saldoResultante(usuario.getSaldo())
                .build();
        transaccionRepository.save(tx);

        Map<String, Object> result = new HashMap<>();
        result.put("monto", amount);
        result.put("saldo", usuario.getSaldo());
        return result;
    }

    public Map<String, Object> getStatus(Long userId) {
        Usuario usuario = usuarioRepository.findById(userId).orElseThrow();

        BigDecimal amount = configuracionRepository.findByClave("daily_reward_amount")
                .map(c -> new BigDecimal(c.getValor()))
                .orElse(BigDecimal.valueOf(500));

        Map<String, Object> result = new HashMap<>();

        if (usuario.getUltimaRecompensaDiaria() == null) {
            result.put("available", true);
            result.put("retryAfter", 0);
            result.put("amount", amount);
            return result;
        }

        long diffSeconds = Duration.between(usuario.getUltimaRecompensaDiaria(), LocalDateTime.now()).getSeconds();
        if (diffSeconds >= HOURS_24_SECONDS) {
            result.put("available", true);
            result.put("retryAfter", 0);
        } else {
            result.put("available", false);
            result.put("retryAfter", HOURS_24_SECONDS - diffSeconds);
        }
        result.put("amount", amount);
        return result;
    }
}
