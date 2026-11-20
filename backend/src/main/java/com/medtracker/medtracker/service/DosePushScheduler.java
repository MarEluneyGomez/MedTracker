package com.medtracker.medtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// A diferencia del auto-skip (encendido por defecto), este job está APAGADO por
// defecto: sin medtracker.push.enabled=true el bean no existe. En D-1 el
// cliente de envío es simulado, así que prenderlo por error crearía
// notificaciones sin entregar nada.
@Slf4j
@Component
@ConditionalOnProperty(name = "medtracker.push.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DosePushScheduler {

    private final DosePushService dosePushService;

    // Cada medtracker.push.interval (por defecto 1 minuto) manda el push de las
    // tomas cuyo momento llegó (RF-06, CU-05). initialDelay evita correr apenas
    // arranca la aplicación. Solo se loguean cantidades, nunca ids de usuario,
    // tokens ni textos; de un fallo solo el nombre de la clase de la excepción
    // (el mensaje o el stack trace podrían traer datos del usuario). Un fallo no
    // frena futuras ejecuciones.
    @Scheduled(fixedDelayString = "${medtracker.push.interval:PT1M}",
            initialDelayString = "${medtracker.push.interval:PT1M}")
    public void sendDuePushes() {
        try {
            DosePushService.PushRunResult result = dosePushService.sendDuePushes();
            if (result.sent() > 0 || result.invalidTokens() > 0
                    || result.transientErrors() > 0 || result.alreadyClaimed() > 0) {
                log.info("Push de recordatorios: {} enviados, {} con token inválido, {} con error transitorio, "
                                + "{} ya reservados",
                        result.sent(), result.invalidTokens(), result.transientErrors(), result.alreadyClaimed());
            }
        } catch (RuntimeException e) {
            log.error("Falló el envío de push de recordatorios ({})", e.getClass().getSimpleName());
        }
    }
}
