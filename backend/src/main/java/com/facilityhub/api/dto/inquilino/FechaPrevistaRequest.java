package com.facilityhub.api.dto.inquilino;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record FechaPrevistaRequest(@NotNull LocalDate fechaPrevista, @NotNull Long version) {}
