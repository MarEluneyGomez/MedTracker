package com.medtracker.medtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    // Todos los días a las 03:30 (en la zona horaria configurada) borra los
    // refresh tokens vencidos. Solo se loguea la cantidad. Un fallo se registra
    // pero no frena futuras ejecuciones.
    @Scheduled(cron = "0 30 3 * * *", zone = "${medtracker.timezone}")
    public void deleteExpiredTokens() {
        try {
            int deleted = refreshTokenService.deleteExpired();
            log.info("Limpieza de refresh tokens: {} vencidos eliminados", deleted);
        } catch (RuntimeException e) {
            log.error("Falló la limpieza de refresh tokens", e);
        }
    }
}
