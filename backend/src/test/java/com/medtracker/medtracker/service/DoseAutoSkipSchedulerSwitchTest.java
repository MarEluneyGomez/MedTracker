package com.medtracker.medtracker.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// El interruptor medtracker.dose.auto-skip.enabled decide si el scheduler existe.
// Sin base de datos ni @EnableScheduling: solo se evalúa la condición del bean.
class DoseAutoSkipSchedulerSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DoseAutoSkipService.class, () -> mock(DoseAutoSkipService.class))
            .withUserConfiguration(DoseAutoSkipScheduler.class);

    @Test
    void withTheSwitchOffTheSchedulerBeanDoesNotExist() {
        runner.withPropertyValues("medtracker.dose.auto-skip.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DoseAutoSkipScheduler.class));
    }

    @Test
    void withTheSwitchOnTheSchedulerBeanExists() {
        runner.withPropertyValues("medtracker.dose.auto-skip.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DoseAutoSkipScheduler.class));
    }

    @Test
    void withoutThePropertyTheSchedulerBeanExists() {
        runner.run(context -> assertThat(context).hasSingleBean(DoseAutoSkipScheduler.class));
    }
}
