package com.facilityhub.api.dto.proveedor;

import jakarta.validation.constraints.NotNull;

public record CambiarActivoRequest(@NotNull Boolean activo) {}
