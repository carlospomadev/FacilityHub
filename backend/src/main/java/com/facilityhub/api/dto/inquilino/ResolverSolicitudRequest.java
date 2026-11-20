package com.facilityhub.api.dto.inquilino;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// ATENDER exige el proveedorId que ADMIN dio de alta; RECHAZAR exige respuesta.
public record ResolverSolicitudRequest(@NotNull AccionSolicitudProveedor accion, Long proveedorId,
                                       @Size(max = 2000) String respuesta, @NotNull Long version) {}
