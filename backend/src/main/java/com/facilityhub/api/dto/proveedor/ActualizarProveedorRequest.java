package com.facilityhub.api.dto.proveedor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// Campos opcionales: solo se aplican los informados. activo=false da de baja la empresa y cierra sus asignaciones activas.
public record ActualizarProveedorRequest(
    @Size(max = 150) String razonSocial,
    @Email @Size(max = 150) String emailContacto,
    @Size(max = 30) String telefono,
    Boolean activo
) {}
