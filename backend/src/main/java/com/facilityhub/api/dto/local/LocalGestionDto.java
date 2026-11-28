package com.facilityhub.api.dto.local;

// FH-014 / RF-04: local para la gestion (activos e inactivos), con su centro, su uso y la huella de sus valores (D7).
public record LocalGestionDto(
    Long id,
    Long centroId,
    String centroCodigo,
    String centroNombre,
    boolean centroActivo,
    String codigo,
    String nombre,
    String planta,
    String ubicacion,
    boolean activo,
    long instalaciones,
    long obligacionesActivas,
    long mantenimientos,
    long ocupaciones,
    long ocupacionesVigentes,
    long ocupacionesFuturas,
    long mantenimientosAbiertos,
    String huella
) {}
