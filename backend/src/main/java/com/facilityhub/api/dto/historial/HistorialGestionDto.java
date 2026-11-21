package com.facilityhub.api.dto.historial;

import com.facilityhub.api.dto.intervencion.DocumentoIntervencionDto;

import java.time.LocalDate;
import java.util.List;

// FH-006 / P6-D3-2: entrada del historial de un local para gestion (ADMIN y EXPLOTACION).
// tipoRegistro: APROBADO | CERRADO_SIN_CUMPLIMIENTO | HISTORICO_P5. Un HISTORICO_P5 no tiene intervencion ni
// estadoValidacion (nunca se presenta como aprobado) y conserva el estado y las fechas reales del registro P5.
// documentos: los de la intervencion, o en HISTORICO_P5 los del mantenimiento; se descargan por los endpoints existentes.
public record HistorialGestionDto(
    String tipoRegistro,
    String etiqueta,
    Long mantenimientoId,
    Long obligacionId,
    String tipoMantenimiento,
    String instalacion,
    String responsable,
    LocalDate fechaProgramada,
    LocalDate fechaRealizacion,
    String estadoMantenimiento,
    String observaciones,
    Long intervencionId,
    LocalDate fechaEjecucion,
    String resultado,
    String estadoValidacion,
    String proveedor,
    List<DocumentoIntervencionDto> documentos
) {}
