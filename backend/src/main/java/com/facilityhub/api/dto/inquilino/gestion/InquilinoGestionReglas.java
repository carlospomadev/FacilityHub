package com.facilityhub.api.dto.inquilino.gestion;

// FH-015 / RF-05 (D3): formato del telefono (opcional): digitos, espacios, +, -, ( y ). Vacio = sin telefono.
public final class InquilinoGestionReglas {
    public static final String TELEFONO = "^[0-9+()\\- ]*$";

    private InquilinoGestionReglas() {}
}
