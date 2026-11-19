package com.facilityhub.api.dto.documento;

import com.facilityhub.domain.VisibilidadDocumento;
import jakarta.validation.constraints.NotNull;

public record CambiarVisibilidadRequest(
    @NotNull VisibilidadDocumento visibilidad
) {}
