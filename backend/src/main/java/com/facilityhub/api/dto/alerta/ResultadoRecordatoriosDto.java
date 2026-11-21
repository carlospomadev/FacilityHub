package com.facilityhub.api.dto.alerta;

import java.time.LocalDate;

// FH-006 / P6-D2: resumen de una ejecucion de recordatorios (solo recuentos, sin datos personales).
public record ResultadoRecordatoriosDto(LocalDate fecha, boolean simulacion, int candidatos, int proximos, int vencidos,
                                        int validacionPendiente, int avisos) {
}
