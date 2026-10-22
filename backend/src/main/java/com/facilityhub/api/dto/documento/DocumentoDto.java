package com.facilityhub.api.dto.documento;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DocumentoDto(
    Long id,
    Long mantenimientoId,
    String nombreOriginal,
    String tipoDocumento,
    LocalDate fechaDocumento,
    LocalDate fechaCaducidad,
    String estadoRevision,
    String observaciones,
    LocalDateTime fechaSubida
) {}
