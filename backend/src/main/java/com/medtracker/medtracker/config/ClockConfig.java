package com.medtracker.medtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    // Un único reloj con zona configurable: "hoy" para la generación de dosis
    // y los timestamps de borrado lógico dependen de la zona del usuario, no
    // de la del servidor (que en la nube suele ser UTC). También permite
    // fijar la hora en los tests.
    @Bean
    public Clock clock(@Value("${medtracker.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
