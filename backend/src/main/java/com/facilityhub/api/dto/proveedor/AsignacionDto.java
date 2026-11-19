package com.facilityhub.api.dto.proveedor;

import java.time.LocalDateTime;

// Vista de gestion de la asignacion vigente de un mantenimiento.
public record AsignacionDto(
    Long id,
    Long mantenimientoId,
    Long proveedorId,
    String proveedor,
    Long tecnicoUsuarioId,
    String tecnico,
    LocalDateTime asignadoAt,
    boolean activa
) {}
