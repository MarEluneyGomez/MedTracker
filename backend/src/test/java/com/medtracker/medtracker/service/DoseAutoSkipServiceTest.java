package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.repository.DoseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoseAutoSkipServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Mock
    private DoseRepository doseRepository;

    private DoseAutoSkipService serviceWithWindow(long minutes) {
        return new DoseAutoSkipService(doseRepository,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), minutes);
    }

    @Test
    void skipsPendingDosesScheduledBeforeNowMinusTheWindow() {
        serviceWithWindow(120).skipOverdue();

        verify(doseRepository).skipPendingBefore(NOW.minusMinutes(120), NOW,
                Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED);
    }

    @Test
    void usesTheConfiguredWindow() {
        serviceWithWindow(30).skipOverdue();

        verify(doseRepository).skipPendingBefore(NOW.minusMinutes(30), NOW,
                Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED);
    }

    @Test
    void usesTheConfiguredTimeZoneInsteadOfUtc() {
        // 15:00Z es 12:00 en Buenos Aires (UTC-3): la hora local, no la de UTC
        Clock buenosAires = Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        new DoseAutoSkipService(doseRepository, buenosAires, 120).skipOverdue();

        verify(doseRepository).skipPendingBefore(LocalDateTime.of(2026, 10, 13, 10, 0),
                LocalDateTime.of(2026, 10, 13, 12, 0),
                Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED);
    }

    @Test
    void returnsTheNumberOfDosesTheRepositorySkipped() {
        when(doseRepository.skipPendingBefore(NOW.minusMinutes(120), NOW,
                Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED)).thenReturn(7);

        assertThat(serviceWithWindow(120).skipOverdue()).isEqualTo(7);
    }

    @Test
    void aWindowOverOneYearIsRejectedAtStartup() {
        assertThatThrownBy(() -> serviceWithWindow(525601))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("medtracker.dose.skip-after-minutes")
                .hasMessageContaining("525600");
        assertThatCode(() -> serviceWithWindow(525600)).doesNotThrowAnyException();
    }

    @Test
    void aWindowOfZeroOrLessIsRejectedAtStartup() {
        assertThatThrownBy(() -> serviceWithWindow(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("medtracker.dose.skip-after-minutes");
        assertThatThrownBy(() -> serviceWithWindow(-5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("medtracker.dose.skip-after-minutes");
    }
}
