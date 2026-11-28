package com.facilityhub.api.dto.inquilino.gestion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// FH-015 / RF-05 (D4): edicion de los datos del inquilino. cif es opcional: si el inquilino ya lo tiene, solo se
// admite si coincide (inmutable; distinto = 400); si es un registro antiguo sin CIF, se asigna una sola vez.
// huella = la recibida al abrir (D7).
public record EditarInquilinoRequest(
    @Size(max = 30) String cif,
    @NotBlank @Size(max = 150) String razonSocial,
    @Size(max = 120) String nombreComercial,
    @Email @Size(max = 150) String emailContacto,
    @Size(max = 30) @Pattern(regexp = InquilinoGestionReglas.TELEFONO) String telefono,
    @NotBlank String huella
) {}
