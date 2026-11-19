package com.facilityhub.p4;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * FH-005A1 / P4 - Reloj fijo en Europe/Madrid, solo para pruebas.
 * 23:30 UTC del 16/11/2026 ya es 17/11/2026 en Madrid: permite distinguir la zona aplicada.
 * No sustituye a RelojConfig; se declara como bean primario adicional.
 */
@TestConfiguration
public class P4RelojConfig {
    public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    public static final Instant INSTANTE = Instant.parse("2026-11-16T23:30:00Z");
    public static final LocalDate HOY_MADRID = LocalDate.of(2026, 11, 17);

    @Bean
    @Primary
    Clock p4RelojFijo() {
        return Clock.fixed(INSTANTE, ZONA);
    }
}
