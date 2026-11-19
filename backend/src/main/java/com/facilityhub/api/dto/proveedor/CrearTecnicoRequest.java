package com.facilityhub.api.dto.proveedor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// La contrasena inicial solo viaja en esta peticion; se guarda con BCrypt y nunca se devuelve ni se registra.
public record CrearTecnicoRequest(
    @NotBlank @Size(max = 80) String nombre,
    @Size(max = 120) String apellidos,
    @NotBlank @Email @Size(max = 150) String email,
    @NotBlank @Size(min = 10, max = 72) String password
) {
    @Override
    public String toString() {
        return "CrearTecnicoRequest[nombre=" + nombre + ", email=<oculto>, password=<oculto>]";
    }
}
