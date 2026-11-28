package com.facilityhub.api.dto.centro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// FH-013 / RF-03 (D4): desactivar (exige confirmarImpacto) o reactivar un centro.
public record CambiarActivoCentroRequest(
    @NotNull Boolean activo,
    @NotBlank String huella,
    Boolean confirmarImpacto
) {}
