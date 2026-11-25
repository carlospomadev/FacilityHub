package com.facilityhub.api.dto.tipo;

import jakarta.validation.constraints.*;

// FH-012 / RF-08: alta de un tipo. El codigo se normaliza a mayusculas y debe cumplir [A-Z0-9-]{2,40} (D4).
public record CrearTipoRequest(
    @NotBlank @Size(max = 40) String codigo,
    @NotBlank @Size(max = 150) String nombre,
    @Size(max = 2000) String descripcion,
    @NotNull @Min(1) @Max(120) Integer periodicidadMeses,
    @NotNull Boolean requiereDocumento,
    @NotBlank @Pattern(regexp = "DESDE_EJECUCION|DESDE_PROGRAMADA") String reglaRecalculo
) {}
