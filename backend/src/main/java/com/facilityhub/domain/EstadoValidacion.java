package com.facilityhub.domain;

// FH-006 / P6-C1: estado de validacion documental de una intervencion, independiente del estado operativo.
public enum EstadoValidacion {
    BORRADOR,
    PENDIENTE_VALIDACION,
    REQUIERE_CORRECCION,
    APROBADO,
    // Intento NO_REALIZADO cerrado por gestion: consta en el historial, no es cumplimiento y no bloquea un nuevo intento.
    CERRADO_SIN_CUMPLIMIENTO;

    public boolean editable() {
        return this == BORRADOR || this == REQUIERE_CORRECCION;
    }
}
