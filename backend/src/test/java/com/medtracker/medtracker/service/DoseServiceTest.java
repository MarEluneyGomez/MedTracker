package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.repository.DoseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoseServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Mock
    private DoseRepository doseRepository;

    private DoseService service;

    @BeforeEach
    void setUp() {
        service = new DoseService(doseRepository, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private Dose dose(Dose.DoseStatus status) {
        return Dose.builder().id(UUID.randomUUID()).scheduledAt(NOW).status(status).build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    @Test
    void confirmingPendingDoseSetsConfirmedAt() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(doseRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        Optional<Dose> result = service.changeStatus(pending.getId(), "CONFIRMED");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.get().getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void reConfirmingAConfirmedDoseReturns409() {
        Dose confirmed = dose(Dose.DoseStatus.CONFIRMED);
        when(doseRepository.findById(confirmed.getId())).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.changeStatus(confirmed.getId(), "CONFIRMED"))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(doseRepository, never()).save(any());
    }

    @Test
    void changingASkippedDoseReturns409() {
        Dose skipped = dose(Dose.DoseStatus.SKIPPED);
        when(doseRepository.findById(skipped.getId())).thenReturn(Optional.of(skipped));

        assertThatThrownBy(() -> service.changeStatus(skipped.getId(), "CONFIRMED"))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void missingDoseStaysEmptySoTheControllerAnswers404() {
        UUID id = UUID.randomUUID();
        when(doseRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.changeStatus(id, "CONFIRMED")).isEmpty();
    }

    @Test
    void registerIgnoresClientSuppliedIdAndDeletedAt() {
        Dose incoming = dose(Dose.DoseStatus.PENDING);
        incoming.setDeletedAt(NOW);
        when(doseRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        Dose saved = service.register(incoming);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getDeletedAt()).isNull();
    }
}
