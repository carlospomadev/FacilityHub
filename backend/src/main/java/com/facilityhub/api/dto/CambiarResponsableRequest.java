package com.facilityhub.api.dto;

import com.facilityhub.domain.ResponsableObligacion;
import jakarta.validation.constraints.NotNull;

public record CambiarResponsableRequest(@NotNull ResponsableObligacion responsable) {}
