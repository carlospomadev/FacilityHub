package com.facilityhub.api.dto.proveedor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearProveedorRequest(
    @Size(max = 20) String cif,
    @NotBlank @Size(max = 150) String razonSocial,
    @Email @Size(max = 150) String emailContacto,
    @Size(max = 30) String telefono
) {}
