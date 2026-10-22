package com.facilityhub.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CrearMantenimientoRequest(
    @NotNull LocalDate fechaProgramada,
    String descripcion
) {}
