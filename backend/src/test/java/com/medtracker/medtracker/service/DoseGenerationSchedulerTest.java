package com.medtracker.medtracker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// La generación automática (medianoche y arranque) no depende de la propiedad
// medtracker.dev-tools.enabled: el scheduler llama directo al service
@ExtendWith(MockitoExtension.class)
class DoseGenerationSchedulerTest {

    @Mock
    private DoseGenerationService doseGenerationService;

    @Test
    void generatesOnStartupAndAtMidnightWithoutAnyDevToolsSwitch() {
        when(doseGenerationService.generateForToday()).thenReturn(2);
        DoseGenerationScheduler scheduler = new DoseGenerationScheduler(doseGenerationService);

        scheduler.generateOnStartup();
        scheduler.generateDailyDoses();

        verify(doseGenerationService, times(2)).generateForToday();
    }
}
