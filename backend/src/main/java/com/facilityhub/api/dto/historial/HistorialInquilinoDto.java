package com.facilityhub.api.dto.historial;

import com.facilityhub.api.dto.inquilino.CumplimientoInquilinoDto;

import java.time.LocalDate;

// FH-006 / P6-D3-2: entrada del historial de un local para el INQUILINO. Solo cumplimientos APROBADOS cubiertos por su
// ocupacion vigente, sin observaciones internas; cumplimiento solo lleva certificados VALIDO + COMPARTIDO (descarga por
// GET /api/documentos/{id}/archivo, misma regla).
public record HistorialInquilinoDto(
    Long mantenimientoId,
    Long obligacionId,
    String tipoMantenimiento,
    String instalacion,
    LocalDate fechaProgramada,
    LocalDate fechaRealizacion,
    CumplimientoInquilinoDto cumplimiento
) {}
