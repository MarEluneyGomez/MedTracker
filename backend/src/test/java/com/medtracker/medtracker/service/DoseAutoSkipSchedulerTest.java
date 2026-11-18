package com.medtracker.medtracker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoseAutoSkipSchedulerTest {

    @Mock
    private DoseAutoSkipService doseAutoSkipService;

    @Test
    void callsTheServiceExactlyOnce() {
        when(doseAutoSkipService.skipOverdue()).thenReturn(3);

        new DoseAutoSkipScheduler(doseAutoSkipService).skipOverdueDoses();

        verify(doseAutoSkipService, times(1)).skipOverdue();
    }

    @Test
    void aFailureIsLoggedAndNotPropagated() {
        when(doseAutoSkipService.skipOverdue()).thenThrow(new IllegalStateException("fallo simulado"));

        assertThatCode(() -> new DoseAutoSkipScheduler(doseAutoSkipService).skipOverdueDoses())
                .doesNotThrowAnyException();
    }

    @Test
    void zeroSkippedDosesDoesNotFail() {
        when(doseAutoSkipService.skipOverdue()).thenReturn(0);

        assertThatCode(() -> new DoseAutoSkipScheduler(doseAutoSkipService).skipOverdueDoses())
                .doesNotThrowAnyException();
        verify(doseAutoSkipService, times(1)).skipOverdue();
    }
}
