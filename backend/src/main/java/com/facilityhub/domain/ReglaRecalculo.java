package com.facilityhub.domain;

import java.time.LocalDate;

// FH-006 / P6-C1 (D-7): regla para calcular la proxima fecha tras una intervencion APROBADA. Se configura por tipo de
// mantenimiento: no todos siguen la misma regla. Nuevas reglas (fechas fijas, requisitos reglamentarios) se anaden aqui
// y en el CHECK de tipos_mantenimiento mediante una migracion nueva.
public enum ReglaRecalculo {
    // Por defecto: fecha real de ejecucion + periodicidad (admite ejecuciones anticipadas, D-4).
    DESDE_EJECUCION {
        @Override
        public LocalDate siguiente(LocalDate fechaEjecucion, LocalDate fechaProgramada, int periodicidadMeses) {
            return fechaEjecucion.plusMonths(periodicidadMeses);
        }
    },
    // Calendario fijo: fecha programada + periodicidad, aunque se ejecutara antes o despues.
    DESDE_PROGRAMADA {
        @Override
        public LocalDate siguiente(LocalDate fechaEjecucion, LocalDate fechaProgramada, int periodicidadMeses) {
            return fechaProgramada.plusMonths(periodicidadMeses);
        }
    };

    public abstract LocalDate siguiente(LocalDate fechaEjecucion, LocalDate fechaProgramada, int periodicidadMeses);
}
