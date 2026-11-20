package com.medtracker.medtracker.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// medtracker.push.provider decide qué PushSender existe. Sin base de datos:
// solo se evalúa la condición del bean.
class LogPushSenderSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LogPushSender.class);

    @Test
    void withProviderLogTheSimulatedSenderExists() {
        runner.withPropertyValues("medtracker.push.provider=log")
                .run(context -> assertThat(context).hasSingleBean(LogPushSender.class));
    }

    @Test
    void withoutThePropertyTheSimulatedSenderExists() {
        runner.run(context -> assertThat(context).hasSingleBean(PushSender.class));
    }

    @Test
    void withProviderFcmTheSimulatedSenderDoesNotExist() {
        runner.withPropertyValues("medtracker.push.provider=fcm")
                .run(context -> assertThat(context).doesNotHaveBean(PushSender.class));
    }

    @Test
    void theSimulatedSenderAlwaysAnswersSent() {
        assertThat(new LogPushSender().send(
                new PushMessage("token", "titulo", "cuerpo", Map.of(), Duration.ofMinutes(1))))
                .isEqualTo(PushResult.SENT);
    }
}
