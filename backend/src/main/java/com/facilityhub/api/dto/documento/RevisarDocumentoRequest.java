package com.facilityhub.api.dto.documento;

import com.facilityhub.domain.EstadoRevisionDocumento;
import jakarta.validation.constraints.NotNull;

public record RevisarDocumentoRequest(
    @NotNull EstadoRevisionDocumento estado,
    String observaciones
) {}
