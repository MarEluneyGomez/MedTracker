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
class DosePushSchedulerTest {

    @Mock
    private DosePushService dosePushService;

    @Test
    void callsTheServiceExactlyOncePerRun() {
        when(dosePushService.sendDuePushes()).thenReturn(new DosePushService.PushRunResult(2, 1, 1, 1));

        new DosePushScheduler(dosePushService).sendDuePushes();

        verify(dosePushService, times(1)).sendDuePushes();
    }

    @Test
    void aFailureIsLoggedAndNotPropagatedAndTheNextRunStillCallsTheService() {
        when(dosePushService.sendDuePushes())
                .thenThrow(new IllegalStateException("fallo simulado"))
                .thenReturn(new DosePushService.PushRunResult(1, 0, 0, 0));
        DosePushScheduler scheduler = new DosePushScheduler(dosePushService);

        assertThatCode(scheduler::sendDuePushes).doesNotThrowAnyException();
        assertThatCode(scheduler::sendDuePushes).doesNotThrowAnyException();

        verify(dosePushService, times(2)).sendDuePushes();
    }

    @Test
    void anAllZeroResultDoesNotFail() {
        when(dosePushService.sendDuePushes()).thenReturn(new DosePushService.PushRunResult(0, 0, 0, 0));

        assertThatCode(() -> new DosePushScheduler(dosePushService).sendDuePushes())
                .doesNotThrowAnyException();
        verify(dosePushService, times(1)).sendDuePushes();
    }
}
