package com.medtracker.medtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DoseGenerationScheduler {

    private final DoseGenerationService doseGenerationService;

    // Todos los días a medianoche (en la zona horaria configurada) genera las
    // dosis del día. Un fallo se registra pero no frena futuras ejecuciones.
    @Scheduled(cron = "0 0 0 * * *", zone = "${medtracker.timezone}")
    public void generateDailyDoses() {
        try {
            int created = doseGenerationService.generateForToday();
            log.info("Generación diaria de dosis: {} dosis creadas", created);
        } catch (RuntimeException e) {
            log.error("Falló la generación diaria de dosis", e);
        }
    }
}
