package com.facilityhub.api.dto;

// FH-010B / RF-09: tipo de mantenimiento activo del catalogo, para el formulario de alta de obligaciones.
public record TipoMantenimientoDto(
    Long id,
    String codigo,
    String nombre,
    String descripcion,
    Integer periodicidadMeses,
    boolean requiereDocumento,
    String reglaRecalculo
) {}
