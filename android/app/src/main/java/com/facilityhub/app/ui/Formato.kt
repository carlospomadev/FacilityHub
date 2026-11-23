package com.facilityhub.app.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// FH-009 / R1: formateadores de presentacion. Solo cambian como se MUESTRA un valor del backend: nunca lo recalculan ni
// alteran lo que se envia a la API (las fechas se siguen enviando en ISO AAAA-MM-DD).

private val ES = Locale.forLanguageTag("es-ES")
private val FECHA_CORTA = DateTimeFormatter.ofPattern("d MMM yyyy", ES)
private val HORA = DateTimeFormatter.ofPattern("HH:mm", ES)

/** "2026-11-16" -> "16 nov 2026". Si el valor no es una fecha ISO, se devuelve tal cual. */
internal fun fechaCorta(valor: String?): String {
    if (valor.isNullOrBlank()) return ""
    return try {
        LocalDate.parse(valor.take(10)).format(FECHA_CORTA).replace(".", "")
    } catch (_: Exception) {
        valor
    }
}

/** "2026-11-21T21:49:05" -> "21 nov 2026, 21:49". Si no se puede interpretar, se muestra recortado como antes. */
internal fun fechaHora(valor: String?): String {
    if (valor.isNullOrBlank()) return ""
    return try {
        val dt = LocalDateTime.parse(valor.take(19))
        "${dt.toLocalDate().format(FECHA_CORTA).replace(".", "")}, ${dt.format(HORA)}"
    } catch (_: Exception) {
        valor.take(16).replace('T', ' ')
    }
}

/** Rol legible para cabeceras ("EXPLOTACION" -> "Explotación"). */
internal fun rolLegible(rol: String?): String = when (rol) {
    "ADMIN" -> "Administración"
    "EXPLOTACION" -> "Explotación"
    "PROVEEDOR" -> "Proveedor"
    "INQUILINO" -> "Inquilino"
    null, "" -> ""
    else -> rol.lowercase(ES).replaceFirstChar { it.titlecase(ES) }
}

internal fun etiquetaCategoria(c: String?): String = when (c) {
    "EVIDENCIA" -> "Evidencia"
    "CERTIFICADO" -> "Certificado"
    "OTRO" -> "Parte u otro"
    null -> "Documento"
    else -> c
}

internal fun etiquetaRevision(e: String?): String = when (e) {
    "PENDIENTE" -> "Pendiente de revisión"
    "VALIDO" -> "Válido"
    "RECHAZADO" -> "Rechazado"
    null -> ""
    else -> e
}

internal fun etiquetaVisibilidad(v: String?): String = if (v == "COMPARTIDO") "Compartido con el inquilino" else "Interno"

internal fun etiquetaEstadoInstalacion(e: String?): String = when (e) {
    "ACTIVA" -> "Activa"
    "INACTIVA" -> "Inactiva"
    // FH-010A: valor de ck_instalacion_estado (antes se mostraba «Fuera_servicio»).
    "FUERA_SERVICIO" -> "Fuera de servicio"
    "BAJA" -> "De baja"
    null -> ""
    else -> e.lowercase(ES).replaceFirstChar { it.titlecase(ES) }
}

internal fun etiquetaEstadoSolicitud(e: String?): String = when (e) {
    "PENDIENTE" -> "Pendiente"
    "ATENDIDA" -> "Atendida"
    "RECHAZADA" -> "Rechazada"
    null -> ""
    else -> e.lowercase(ES).replaceFirstChar { it.titlecase(ES) }
}
