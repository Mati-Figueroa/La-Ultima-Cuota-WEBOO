package com.ultimacuota.scheduler;

import com.ultimacuota.services.HorseConditionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Curación natural de lesiones y pérdida lenta de felicidad. */
@Component
@RequiredArgsConstructor
@Slf4j
public class HorseCareScheduler {

    private final HorseConditionService horseConditionService;

    @Scheduled(fixedRate = 60000, initialDelay = 5000)
    public void sanarLesiones() {
        try {
            int sanados = horseConditionService.sanarLesionesVencidas();
            if (sanados > 0) log.info("[Cuidados] {} caballo(s) sanaron de su lesión", sanados);
        } catch (Exception e) {
            log.error("[Cuidados] Error sanando lesiones: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 3600000, initialDelay = 3600000)
    public void decaerFelicidad() {
        try {
            horseConditionService.decaerFelicidad(2);
        } catch (Exception e) {
            log.error("[Cuidados] Error actualizando felicidad: {}", e.getMessage());
        }
    }
}
