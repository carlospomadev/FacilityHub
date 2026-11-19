package com.facilityhub.api.dto.proveedor;

import java.util.List;

// Solo para ADMIN y EXPLOTACION.
public record ProveedorDto(
    Long id,
    String cif,
    String razonSocial,
    String emailContacto,
    String telefono,
    boolean activo,
    List<TecnicoDto> tecnicos
) {}
