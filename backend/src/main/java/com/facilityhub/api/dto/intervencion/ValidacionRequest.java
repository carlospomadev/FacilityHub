package com.facilityhub.api.dto.intervencion;

import com.facilityhub.domain.AccionValidacion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// APROBAR o SOLICITAR_CORRECCION (comentario obligatorio). documentosCompartidos: certificados de la intervencion que
// gestion decide compartir explicitamente con el inquilino al aprobar (D-11); el resto queda INTERNO.
public record ValidacionRequest(
    @NotNull AccionValidacion accion,
    @Size(max = 4000) String comentario,
    @NotNull Long version,
    List<Long> documentosCompartidos
) {}
