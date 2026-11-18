package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.repository.DoseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

// Omisión automática de tomas (RN-04): una toma PENDING cuyo horario pasó hace
// más de la ventana configurada se marca SKIPPED. Este bean existe siempre; lo
// que se enciende o apaga por propiedad es el scheduler que lo invoca.
@Service
public class DoseAutoSkipService {

    // Tope de la ventana: un año en minutos
    private static final long MAX_SKIP_AFTER_MINUTES = 365L * 24 * 60;

    private final DoseRepository doseRepository;
    private final Clock clock;
    private final long skipAfterMinutes;

    public DoseAutoSkipService(DoseRepository doseRepository, Clock clock,
                               @Value("${medtracker.dose.skip-after-minutes}") long skipAfterMinutes) {
        if (skipAfterMinutes < 1) {
            throw new IllegalStateException(
                    "medtracker.dose.skip-after-minutes debe ser al menos 1 (minutos), pero es " + skipAfterMinutes);
        }
        if (skipAfterMinutes > MAX_SKIP_AFTER_MINUTES) {
            throw new IllegalStateException(
                    "medtracker.dose.skip-after-minutes no puede superar " + MAX_SKIP_AFTER_MINUTES
                            + " (un año en minutos), pero es " + skipAfterMinutes);
        }
        this.doseRepository = doseRepository;
        this.clock = clock;
        this.skipAfterMinutes = skipAfterMinutes;
    }

    // Omite las tomas pendientes anteriores a (ahora - ventana), en la zona
    // horaria configurada, y devuelve cuántas omitió. Idempotente.
    @Transactional
    public int skipOverdue() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime limit = now.minusMinutes(skipAfterMinutes);
        return doseRepository.skipPendingBefore(limit, now, Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED);
    }
}
