package com.facilityhub.api.dto;

import com.facilityhub.domain.EstadoMantenimiento;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CambiarEstadoMantenimientoRequest(
    @NotNull EstadoMantenimiento estado,
    LocalDate fechaRealizacion,
    String observaciones
) {}
