package com.facilityhub.api.dto;

import java.time.LocalDate;

public record MantenimientoDto(
    Long id,
    Long obligacionId,
    LocalDate fechaProgramada,
    LocalDate fechaRealizacion,
    String estado,
    String descripcion,
    String observaciones
) {}
