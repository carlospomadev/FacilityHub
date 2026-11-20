package com.facilityhub.api.dto.inquilino;

import jakarta.validation.constraints.NotNull;

public record DeclararProveedorRequest(@NotNull Long proveedorId) {}
