package com.ultimacuota.scheduler;

import com.ultimacuota.models.*;
import com.ultimacuota.repositories.*;
import com.ultimacuota.services.RaceSimulationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class RaceCreator {

    private final CarreraRepository carreraRepository;
    private final InscripcionRepository inscripcionRepository;
    private final CaballoRepository caballoRepository;
    private final ApuestaRepository apuestaRepository;
    private final RaceSimulationService simulationService;

    @Transactional
    public Carrera createRaceAt(LocalDateTime startIn) {
        try {
            Carrera race = Carrera.builder()
                    .nombre(null)
                    .estado("programada")
                    .fechaProgramada(startIn)
                    .cupoMaximo(12)
                    .tieneInteraccionHumana(false)
                    .build();
            race = carreraRepository.save(race);

            int numBots = new Random().nextInt(4) + 6;
            List<Caballo> botHorses = caballoRepository.findRandomBotHorses();
            if (botHorses.size() > numBots) {
                botHorses = botHorses.subList(0, numBots);
            }

            int carril = 1;
            for (Caballo bot : botHorses) {
                Inscripcion inscripcion = Inscripcion.builder()
                        .carrera(race)
                        .caballo(bot)
                        .usuario(null)
                        .numeroCarril(carril++)
                        .build();
                inscripcionRepository.save(inscripcion);
            }

            log.info("[RaceCreator] Carrera #{} creada con {} bots para {}",
                    race.getId(), botHorses.size(), startIn);

            List<Inscripcion> allInscriptions = inscripcionRepository.findByCarreraIdWithCaballo(race.getId());
            createBotBets(race.getId(), allInscriptions);

            return race;
        } catch (Exception e) {
            log.error("[RaceCreator] Error creando carrera: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public Carrera createRaceWithBots(int offsetMinutes) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startIn;
        if (offsetMinutes == 0) {
            startIn = now.plusMinutes(1).withSecond(0).withNano(0);
        } else {
            startIn = now.plusMinutes(offsetMinutes).withSecond(0).withNano(0);
        }
        return createRaceAt(startIn);
    }

    @Transactional
    public void createBotBets(Long raceId, List<Inscripcion> inscriptions) {
        try {
            List<Map<String, Object>> botBetData = simulationService.generateBotBets(raceId, inscriptions);

            BigDecimal totalPool = BigDecimal.ZERO;
            List<Apuesta> allBotBets = new ArrayList<>();

            for (Map<String, Object> bd : botBetData) {
                Caballo caballo = caballoRepository.getReferenceById((Long) bd.get("caballo_id"));
                Apuesta bet = Apuesta.builder()
                        .usuario(null)
                        .carrera(carreraRepository.getReferenceById(raceId))
                        .caballo(caballo)
                        .monto(new BigDecimal(bd.get("monto").toString()))
                        .cuota(BigDecimal.ONE)
                        .estado("pendiente")
                        .build();
                allBotBets.add(bet);
                totalPool = totalPool.add(bet.getMonto());
            }

            for (Apuesta bet : allBotBets) {
                apuestaRepository.save(bet);
            }

            log.info("[RaceCreator] Carrera #{}: {} apuestas de bots creadas, pool total: ${}",
                    raceId, allBotBets.size(), totalPool);
        } catch (Exception e) {
            log.error("[RaceCreator] Error creando apuestas bots carrera #{}: {}", raceId, e.getMessage());
        }
    }

    @Transactional
    public void fillWithBots(Long raceId) {
        try {
            List<Inscripcion> existing = inscripcionRepository.findByCarreraIdWithCaballo(raceId);
            List<Long> existingIds = existing.stream().map(i -> i.getCaballo().getId()).toList();
            int emptySlots = 12 - existing.size();
            int minBots = (int) Math.ceil(emptySlots / 2.0);
            int maxBots = (int) Math.floor(emptySlots * 3.0 / 4);
            int numBots = new Random().nextInt(Math.max(maxBots - minBots + 1, 1)) + minBots;

            List<Caballo> botHorses = caballoRepository.findRandomBotHorses();
            botHorses = botHorses.stream()
                    .filter(b -> !existingIds.contains(b.getId()))
                    .limit(numBots).toList();

            int carril = existing.size() + 1;
            for (Caballo bot : botHorses) {
                Inscripcion inscripcion = Inscripcion.builder()
                        .carrera(carreraRepository.getReferenceById(raceId))
                        .caballo(bot)
                        .usuario(null)
                        .numeroCarril(carril++)
                        .build();
                inscripcionRepository.save(inscripcion);
            }

            log.info("[RaceCreator] Carrera #{} rellenada con {} bots", raceId, botHorses.size());

            List<Inscripcion> allInscriptions = inscripcionRepository.findByCarreraIdWithCaballo(raceId);
            createBotBets(raceId, allInscriptions);
        } catch (Exception e) {
            log.error("[RaceCreator] Error rellenando carrera #{}: {}", raceId, e.getMessage());
        }
    }
}
