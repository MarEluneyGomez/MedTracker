package com.medtracker.medtracker.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// El interruptor medtracker.push.enabled decide si el scheduler existe, y está
// apagado por defecto. Sin base de datos ni @EnableScheduling: solo se evalúa
// la condición del bean.
class DosePushSchedulerSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DosePushService.class, () -> mock(DosePushService.class))
            .withUserConfiguration(DosePushScheduler.class);

    @Test
    void withTheSwitchOffTheSchedulerBeanDoesNotExist() {
        runner.withPropertyValues("medtracker.push.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DosePushScheduler.class));
    }

    @Test
    void withTheSwitchOnTheSchedulerBeanExists() {
        runner.withPropertyValues("medtracker.push.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DosePushScheduler.class));
    }

    @Test
    void withoutThePropertyTheSchedulerBeanDoesNotExist() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DosePushScheduler.class));
    }
}
