package com.facilityhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class RelojConfig {

    // "Hoy" se evalua en la zona de los centros; inyectable para fijar la fecha en pruebas.
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Madrid"));
    }
}
