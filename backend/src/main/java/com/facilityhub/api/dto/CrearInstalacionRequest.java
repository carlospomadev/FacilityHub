package com.facilityhub.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// FH-010A / RF-07: alta de una instalacion en un centro. localId nulo = zona comun del centro (esquema V5).
// Longitudes de nombre y tipo = columnas VARCHAR(120)/VARCHAR(80); estado = valores de ck_instalacion_estado
// (si no se informa, ACTIVA, como el DEFAULT de la columna).
public record CrearInstalacionRequest(
    Long localId,
    @NotBlank @Size(max = 120) String nombre,
    @NotBlank @Size(max = 80) String tipo,
    @Size(max = 2000) String descripcion,
    @Pattern(regexp = "ACTIVA|INACTIVA|FUERA_SERVICIO") String estado
) {}
