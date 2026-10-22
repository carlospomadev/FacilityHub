package com.facilityhub.api.dto;

import java.time.LocalDate;

public record ObligacionDto(
    Long id,
    Long localId,
    String instalacion,
    String tipoMantenimiento,
    Integer periodicidadMeses,
    LocalDate proximaFecha,
    String estado
) {}
