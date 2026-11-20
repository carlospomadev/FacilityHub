package com.facilityhub.api.dto.proveedor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// FH-006: trabajo visible para el PROVEEDOR. Solo los datos necesarios para ejecutar el trabajo: sin observaciones
// internas, sin datos del inquilino y sin identificadores para navegar por centros, locales u obligaciones.
public record TrabajoProveedorDto(
    Long mantenimientoId,
    LocalDate fechaProgramada,
    String estado,
    String descripcion,
    String tipoMantenimiento,
    String centro,
    String local,
    String instalacion,
    String responsable,
    String tecnicoAsignado,
    LocalDateTime asignadoAt,
    List<String> accionesPermitidas,
    // FH-006 / P6-C1: intervencion mas reciente de su empresa en este trabajo (si existe) y su estado de validacion.
    Long intervencionId,
    String estadoValidacion
) {}
