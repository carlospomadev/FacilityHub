package com.facilityhub.api.dto;

// FH-010A / RF-07: instalacion con su ubicacion dentro del centro (local nulo = zona comun).
public record InstalacionCentroDto(
    Long id,
    String nombre,
    String tipo,
    String descripcion,
    String estado,
    Long centroId,
    Long localId,
    String localCodigo,
    String localNombre
) {}
