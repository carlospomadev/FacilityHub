package com.facilityhub.api.dto.inquilino.gestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// FH-015 / RF-05 (D5): desactivar (exige confirmarImpacto) o reactivar un inquilino.
public record CambiarActivoInquilinoRequest(
    @NotNull Boolean activo,
    @NotBlank String huella,
    Boolean confirmarImpacto
) {}
