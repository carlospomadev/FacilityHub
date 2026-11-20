package com.facilityhub.domain;

public enum AccionValidacion {
    APROBAR,
    SOLICITAR_CORRECCION,
    // Solo para NO_REALIZADO pendiente de validacion; comentario obligatorio.
    CERRAR_SIN_CUMPLIMIENTO
}
