package com.facilityhub.api.dto.intervencion;

import java.time.LocalDateTime;

public record DocumentoIntervencionDto(
    Long id,
    String nombreOriginal,
    String categoria,
    String estadoRevision,
    String visibilidad,
    LocalDateTime fechaSubida
) {}
