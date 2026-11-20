package com.facilityhub.domain;

public enum OrigenIntervencion {
    // Registrada por un tecnico del proveedor asignado.
    PROVEEDOR,
    // Registrada por gestion para un proveedor externo sin acceso a FacilityHub (D-2). Requiere aprobacion separada.
    REGISTRO_GESTION
}
