package com.facilityhub.api.dto.tipo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// FH-012 / RF-08 (D7): desactivar o reactivar. Desactivar un tipo con obligaciones activas exige confirmarImpacto.
public record CambiarActivoTipoRequest(
    @NotNull Boolean activo,
    @NotBlank String huella,
    Boolean confirmarImpacto
) {}
