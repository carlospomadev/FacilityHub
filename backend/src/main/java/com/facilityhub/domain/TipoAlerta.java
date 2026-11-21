package com.facilityhub.domain;

// FH-006 / P6-D1: alertas internas por eventos. P6-D2 (V9): recordatorios de vencimiento.
public enum TipoAlerta {
    ASIGNACION,
    PROVEEDOR_DECLARADO,
    EVIDENCIAS_PRESENTADAS,
    CORRECCION_SOLICITADA,
    APROBADO,
    VENCIMIENTO_PROXIMO,
    MANTENIMIENTO_VENCIDO,
    // Mantenimiento vencido cuya intervencion espera validacion (documentacion pendiente, no incumplimiento del proveedor).
    VALIDACION_PENDIENTE_VENCIDA
}
