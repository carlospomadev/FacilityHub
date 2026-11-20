package com.facilityhub.api.dto.inquilino;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// RECHAZAR exige motivo.
public record RevisionDeclaracionRequest(@NotNull AccionRevisionProveedor accion, @Size(max = 2000) String motivo,
                                         @NotNull Long version) {}
