package com.facilityhub.api.dto.intervencion;

import java.time.LocalDateTime;

public record ValidacionDto(
    int numeroEnvio,
    String accion,
    String comentario,
    String validadaPor,
    LocalDateTime fecha
) {}
