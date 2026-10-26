package com.medtracker.medtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DoseGenerationScheduler {

    private final DoseGenerationService doseGenerationService;

    // Al arrancar la aplicación recupera lo que el job de medianoche no pudo
    // generar si el servidor estaba caído. Es idempotente: si el job ya corrió
    // ese día no duplica nada.
    @EventListener(ApplicationReadyEvent.class)
    public void generateOnStartup() {
        generateDailyDoses();
    }

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
