package com.facilityhub.api.dto;

import java.time.LocalDate;

// FH-005A2-C (D1): vista minima de un mantenimiento para INQUILINO. No declara observaciones internas
// ni otros metadatos de gestion. Los nombres coinciden con MantenimientoDto para que Android (Gson,
// observaciones nullable) siga deserializando la respuesta sin cambios.
public record MantenimientoInquilinoDto(
    Long id,
    Long obligacionId,
    LocalDate fechaProgramada,
    LocalDate fechaRealizacion,
    String estado,
    String descripcion
) {}
