package com.facilityhub.api.dto.local;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// FH-014 / RF-04 (D4, D5): desactivar (exige confirmarImpacto) o reactivar un local.
public record CambiarActivoLocalRequest(
    @NotNull Boolean activo,
    @NotBlank String huella,
    Boolean confirmarImpacto
) {}
