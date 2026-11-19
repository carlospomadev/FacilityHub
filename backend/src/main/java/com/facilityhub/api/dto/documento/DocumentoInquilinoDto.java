package com.facilityhub.api.dto.documento;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Proyeccion para INQUILINO: sin observaciones de revision, ruta de almacenamiento ni visibilidad.
public record DocumentoInquilinoDto(
    Long id,
    Long mantenimientoId,
    String nombreOriginal,
    String tipoDocumento,
    LocalDate fechaDocumento,
    LocalDate fechaCaducidad,
    String estadoRevision,
    LocalDateTime fechaSubida
) implements DocumentoRespuesta {}
