package com.facilityhub.api.dto.proveedor;

import jakarta.validation.constraints.NotNull;

public record AsignacionRequest(
    @NotNull Long proveedorId,
    Long tecnicoUsuarioId
) {}
