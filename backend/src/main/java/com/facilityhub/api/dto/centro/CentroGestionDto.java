package com.facilityhub.api.dto.centro;

// FH-013 / RF-03: centro para la gestion (activos e inactivos), con su uso y la huella de sus valores (D7).
public record CentroGestionDto(
    Long id,
    String codigo,
    String nombre,
    String direccion,
    String ciudad,
    String descripcion,
    boolean activo,
    long localesActivos,
    long locales,
    long instalaciones,
    long obligacionesActivas,
    long ocupacionesVigentes,
    long ocupacionesFuturas,
    long mantenimientosAbiertos,
    String huella
) {}
