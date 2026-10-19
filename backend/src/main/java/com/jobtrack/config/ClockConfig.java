package com.jobtrack.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Reloj inyectable para poder fijar el tiempo en las pruebas. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
