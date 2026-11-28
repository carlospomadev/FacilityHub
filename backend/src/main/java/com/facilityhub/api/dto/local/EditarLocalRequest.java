package com.facilityhub.api.dto.local;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// FH-014 / RF-04: edicion de los datos descriptivos. codigo y centroId son opcionales y solo se admiten si coinciden
// con los actuales (D2 y D6: inmutables; un valor distinto es un 400 explicito). huella = la recibida al abrir (D7).
public record EditarLocalRequest(
    String codigo,
    Long centroId,
    @NotBlank @Size(max = 120) String nombre,
    @Size(max = 40) String planta,
    @Size(max = 150) String ubicacion,
    @NotBlank String huella
) {}
