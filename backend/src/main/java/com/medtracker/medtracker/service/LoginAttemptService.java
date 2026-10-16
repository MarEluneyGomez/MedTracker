package com.medtracker.medtracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

// Limita los intentos de login fallidos (CU-02, flujo 2b). Vive en memoria:
// alcanza mientras haya una sola instancia y se reinicia con la app; si se
// escala a varias instancias habrá que moverlo a un almacén compartido.
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);
    // Evita que el mapa crezca sin límite con emails inventados
    private static final int PURGE_THRESHOLD = 10_000;

    private record Attempts(int failures, Instant lastFailureAt, Instant lockedUntil) {
    }

    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    // Corta con 429 si la clave (email o IP) está bloqueada
    public void ensureNotLocked(String key) {
        Attempts current = attempts.get(key);
        if (current != null && current.lockedUntil() != null && current.lockedUntil().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Probá de nuevo más tarde");
        }
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        if (attempts.size() > PURGE_THRESHOLD) {
            attempts.values().removeIf(a -> isStale(a, now));
        }
        attempts.compute(key, (k, current) -> {
            // Los fallos viejos (o de un bloqueo ya vencido) no cuentan
            int previous = current == null || isStale(current, now) ? 0 : current.failures();
            int failures = previous + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCK_TIME) : null;
            return new Attempts(failures, now, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private boolean isStale(Attempts a, Instant now) {
        return a.lastFailureAt().plus(LOCK_TIME).isBefore(now);
    }
}
