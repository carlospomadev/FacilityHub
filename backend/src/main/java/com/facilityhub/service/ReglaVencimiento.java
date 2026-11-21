package com.facilityhub.service;

import java.time.LocalDate;

// FH-006 / P6-D2-D3: regla unica de vencimiento de un mantenimiento abierto (recordatorios y panel de gestion).
// Pura: sin acceso a datos. "enValidacion" = existe una intervencion en PENDIENTE_VALIDACION (enviado, no cumplido).
//  - PROXIMO: hoy <= fecha <= hoy + preaviso y sin intervencion presentada.
//  - VENCIDO: fecha < hoy sin intervencion presentada (sin intervencion, BORRADOR, REQUIERE_CORRECCION...).
//  - VALIDACION_PENDIENTE_VENCIDA: fecha < hoy con intervencion presentada.
//  - FUERA: mas alla del preaviso, o futuro ya presentado (depende de la validacion, no del vencimiento).
public final class ReglaVencimiento {
    public enum Situacion { PROXIMO, VENCIDO, VALIDACION_PENDIENTE_VENCIDA, FUERA }

    private ReglaVencimiento() {}

    public static Situacion situacion(LocalDate fechaProgramada, LocalDate hoy, int preavisoDias, boolean enValidacion) {
        if (!fechaProgramada.isBefore(hoy)) {
            if (fechaProgramada.isAfter(hoy.plusDays(preavisoDias)) || enValidacion) {
                return Situacion.FUERA;
            }
            return Situacion.PROXIMO;
        }
        return enValidacion ? Situacion.VALIDACION_PENDIENTE_VENCIDA : Situacion.VENCIDO;
    }
}
