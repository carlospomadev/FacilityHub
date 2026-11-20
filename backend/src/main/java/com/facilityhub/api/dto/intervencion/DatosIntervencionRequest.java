package com.facilityhub.api.dto.intervencion;

import com.facilityhub.domain.ResultadoIntervencion;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// Alta (sin version) o edicion (con version, obligatoria) de los datos de una intervencion. Campos nulos = sin cambio.
public record DatosIntervencionRequest(
    Long version,
    LocalDate fechaEjecucion,
    @Size(max = 4000) String actuaciones,
    ResultadoIntervencion resultado,
    @Size(max = 4000) String incidencias
) {}
