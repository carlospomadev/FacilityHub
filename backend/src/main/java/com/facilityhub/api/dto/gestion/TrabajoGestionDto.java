package com.facilityhub.api.dto.gestion;

import java.time.LocalDate;

// FH-006 / P6-D3: fila del panel de gestion. local es null y zonaComun true en obligaciones sin local.
// intervencionId/estadoIntervencion: intervencion abierta (o la de la bandeja) si existe. proveedorAsignado: asignacion
// activa; si no la hay, el proveedor de la intervencion. diasHastaVencimiento < 0 si esta vencido.
public record TrabajoGestionDto(
    Long mantenimientoId,
    Long intervencionId,
    String tipoMantenimiento,
    String centro,
    String local,
    boolean zonaComun,
    String responsable,
    LocalDate fechaProgramada,
    LocalDate fechaRealizacion,
    String estado,
    String estadoIntervencion,
    String proveedorAsignado,
    long diasHastaVencimiento
) {}
