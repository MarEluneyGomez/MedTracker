package com.medtracker.medtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "medtracker.dose.auto-skip.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DoseAutoSkipScheduler {

    private final DoseAutoSkipService doseAutoSkipService;

    // Cada medtracker.dose.auto-skip.interval (por defecto 5 minutos) omite las
    // tomas pendientes vencidas (RN-04). initialDelay evita correr apenas arranca
    // la aplicación. Solo se loguea la cantidad. Un fallo se registra pero no
    // frena futuras ejecuciones.
    @Scheduled(fixedDelayString = "${medtracker.dose.auto-skip.interval:PT5M}",
            initialDelayString = "${medtracker.dose.auto-skip.interval:PT5M}")
    public void skipOverdueDoses() {
        try {
            int skipped = doseAutoSkipService.skipOverdue();
            if (skipped > 0) {
                log.info("Omisión automática de tomas: {} tomas marcadas como omitidas", skipped);
            }
        } catch (RuntimeException e) {
            log.error("Falló la omisión automática de tomas", e);
        }
    }
}
