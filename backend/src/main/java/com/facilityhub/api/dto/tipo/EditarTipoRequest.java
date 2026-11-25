package com.facilityhub.api.dto.tipo;

import jakarta.validation.constraints.*;

// FH-012 / RF-08: edicion de un tipo (todos los campos editables). codigo es opcional y solo se admite si coincide con
// el actual (D4: inmutable). huella = la recibida al abrir el formulario (D6). confirmarImpacto: obligatorio si cambian
// periodicidad, regla o documento y el tipo tiene obligaciones activas (D2).
public record EditarTipoRequest(
    String codigo,
    @NotBlank @Size(max = 150) String nombre,
    @Size(max = 2000) String descripcion,
    @NotNull @Min(1) @Max(120) Integer periodicidadMeses,
    @NotNull Boolean requiereDocumento,
    @NotBlank @Pattern(regexp = "DESDE_EJECUCION|DESDE_PROGRAMADA") String reglaRecalculo,
    @NotBlank String huella,
    Boolean confirmarImpacto
) {}
