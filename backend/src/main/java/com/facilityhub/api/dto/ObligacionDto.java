package com.facilityhub.api.dto;

import java.time.LocalDate;

// FH-006: se anaden centroId y responsable al final (campos aditivos, compatibles con el cliente Android P5).
public record ObligacionDto(
    Long id,
    Long localId,
    String instalacion,
    String tipoMantenimiento,
    Integer periodicidadMeses,
    LocalDate proximaFecha,
    String estado,
    Long centroId,
    String responsable
) {}
