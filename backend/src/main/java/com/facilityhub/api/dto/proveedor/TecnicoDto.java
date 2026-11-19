package com.facilityhub.api.dto.proveedor;

// Sin hash de contrasena ni datos de sesion.
public record TecnicoDto(
    Long id,
    String nombre,
    String apellidos,
    String email,
    boolean activo
) {}
