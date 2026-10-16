package com.medtracker.medtracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    // Reloj que se puede adelantar para simular el paso del tiempo
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new LoginAttemptService(clock);
    }

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure("email:a@a.com");
        }
    }

    @Test
    void doesNotLockBeforeMaxFailures() {
        fail(LoginAttemptService.MAX_FAILURES - 1);

        assertThatCode(() -> service.ensureNotLocked("email:a@a.com")).doesNotThrowAnyException();
    }

    @Test
    void locksAfterMaxFailuresWith429() {
        fail(LoginAttemptService.MAX_FAILURES);

        assertThatThrownBy(() -> service.ensureNotLocked("email:a@a.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void unlocksWhenLockTimeElapses() {
        fail(LoginAttemptService.MAX_FAILURES);
        clock.advance(LoginAttemptService.LOCK_TIME.plusSeconds(1));

        assertThatCode(() -> service.ensureNotLocked("email:a@a.com")).doesNotThrowAnyException();
    }

    @Test
    void successResetsCounter() {
        fail(LoginAttemptService.MAX_FAILURES - 1);
        service.recordSuccess("email:a@a.com");
        fail(LoginAttemptService.MAX_FAILURES - 1);

        assertThatCode(() -> service.ensureNotLocked("email:a@a.com")).doesNotThrowAnyException();
    }

    @Test
    void keysAreIndependent() {
        fail(LoginAttemptService.MAX_FAILURES);

        assertThatCode(() -> service.ensureNotLocked("email:otro@a.com")).doesNotThrowAnyException();
    }
}
