package com.medtracker.medtracker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenService refreshTokenService;
    @InjectMocks
    private RefreshTokenCleanupScheduler scheduler;

    @Test
    void deletesTheExpiredTokens() {
        when(refreshTokenService.deleteExpired()).thenReturn(4);

        scheduler.deleteExpiredTokens();

        verify(refreshTokenService).deleteExpired();
    }

    @Test
    void aFailureIsSwallowedSoTheNextRunStillHappens() {
        when(refreshTokenService.deleteExpired()).thenThrow(new IllegalStateException("base caída"));

        assertDoesNotThrow(scheduler::deleteExpiredTokens);
    }
}
