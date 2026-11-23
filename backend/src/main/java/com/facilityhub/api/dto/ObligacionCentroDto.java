package com.facilityhub.api.dto;

import java.time.LocalDate;

// FH-010B / RF-09: obligacion con su identificacion y ubicacion completas (respuesta del alta y consulta por centro).
// DTO nuevo: no cambia ObligacionDto, que usan los endpoints existentes.
public record ObligacionCentroDto(
    Long id,
    Long centroId,
    Long localId,
    String localCodigo,
    String localNombre,
    Long instalacionId,
    String instalacion,
    Long tipoMantenimientoId,
    String tipoMantenimiento,
    Integer periodicidadMeses,
    String responsable,
    LocalDate fechaInicio,
    LocalDate proximaFecha,
    String estado,
    boolean activo,
    String observaciones
) {}
