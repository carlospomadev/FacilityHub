package com.facilityhub.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// FH-010B / RF-09: alta de una obligacion periodica en un centro. localId nulo = zona comun (o se completa desde una
// instalacion de local). proximaFecha nula = fechaInicio + periodicidad del tipo. Fechas ISO sin zona horaria.
public record CrearObligacionRequest(
    Long localId,
    Long instalacionId,
    @NotNull Long tipoMantenimientoId,
    @NotBlank @Pattern(regexp = "NHOOD|INQUILINO") String responsable,
    @NotNull LocalDate fechaInicio,
    LocalDate proximaFecha,
    @Size(max = 2000) String observaciones
) {}
