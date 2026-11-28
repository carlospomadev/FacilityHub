package com.facilityhub.api.dto.centro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// FH-013 / RF-03: alta de un centro. Longitudes = columnas de la tabla centros (V1). El codigo se normaliza a
// mayusculas y debe cumplir [A-Z0-9-]{2,30} (D2). Obligatorios: codigo, nombre y ciudad (D5).
public record CrearCentroRequest(
    @NotBlank @Size(max = 30) String codigo,
    @NotBlank @Size(max = 150) String nombre,
    @Size(max = 200) String direccion,
    @NotBlank @Size(max = 100) String ciudad,
    @Size(max = 2000) String descripcion
) {}
