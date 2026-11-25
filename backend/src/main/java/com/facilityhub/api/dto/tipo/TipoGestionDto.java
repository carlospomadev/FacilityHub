package com.facilityhub.api.dto.tipo;

// FH-012 / RF-08: tipo de mantenimiento para la gestion del catalogo (activos e inactivos), con su uso y su huella.
// huella = resumen de los valores actuales; el cliente la devuelve al guardar para detectar cambios de otro usuario (D6).
public record TipoGestionDto(
    Long id,
    String codigo,
    String nombre,
    String descripcion,
    Integer periodicidadMeses,
    boolean requiereDocumento,
    String reglaRecalculo,
    boolean activo,
    long obligacionesActivas,
    String huella
) {}
