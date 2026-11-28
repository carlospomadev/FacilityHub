package com.facilityhub.api.dto.local;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// FH-014 / RF-04: alta de un local. El centro es el de la ruta. Longitudes = columnas de la tabla locales (V1).
// Obligatorios: codigo y nombre (D3). El codigo se normaliza a mayusculas sin espacios exteriores (D2).
public record CrearLocalRequest(
    @NotBlank @Size(max = 40) String codigo,
    @NotBlank @Size(max = 120) String nombre,
    @Size(max = 40) String planta,
    @Size(max = 150) String ubicacion
) {}
