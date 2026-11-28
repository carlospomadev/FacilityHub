package com.facilityhub.api.dto.inquilino.gestion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// FH-015 / RF-05: alta de un inquilino. Longitudes = columnas de la tabla inquilinos (V1).
// Obligatorios: CIF y razon social (D3). El CIF se normaliza en el servicio (D2).
public record CrearInquilinoRequest(
    @NotBlank @Size(max = 30) String cif,
    @NotBlank @Size(max = 150) String razonSocial,
    @Size(max = 120) String nombreComercial,
    @Email @Size(max = 150) String emailContacto,
    @Size(max = 30) @Pattern(regexp = InquilinoGestionReglas.TELEFONO) String telefono
) {}
