package com.facilityhub.api.dto.centro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// FH-013 / RF-03: edicion de los datos descriptivos. codigo es opcional y solo se admite si coincide con el actual
// (D2: inmutable). huella = la recibida al abrir el formulario (D7).
public record EditarCentroRequest(
    String codigo,
    @NotBlank @Size(max = 150) String nombre,
    @Size(max = 200) String direccion,
    @NotBlank @Size(max = 100) String ciudad,
    @Size(max = 2000) String descripcion,
    @NotBlank String huella
) {}
