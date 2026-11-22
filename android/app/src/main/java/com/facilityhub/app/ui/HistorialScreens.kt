package com.facilityhub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.HistorialGestionDto
import com.facilityhub.app.data.HistorialInquilinoDto
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons
import com.facilityhub.app.ui.theme.FhTema

// FH-006 / P6-D3-4: historial de mantenimientos del local. Contenido decidido por el backend segun el rol:
//  - gestion: aprobados, cierres sin cumplimiento e historicos P5 ("Historico P5 — sin validacion", con su estado real);
//  - INQUILINO: solo cumplimientos aprobados de su ocupacion vigente con certificados VALIDO + COMPARTIDO.
// Las descargas pasan por GET /api/documentos/{id}/archivo, que vuelve a comprobar la autorizacion.
// FH-009 / R1: tarjetas con chip de tipo de registro (el P5 con estilo atenuado y borde), fechas legibles y documentos con
// icono. La linea de tiempo completa llega en R6.
@Composable
fun HistorialLocalScreen(
    historial: HistorialViewModel,
    flujo: FlujoViewModel,
    localId: Long,
    titulo: String,
    onSesionCaducada: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(localId) { flujo.limpiarMensajes(); historial.cargar(localId) }
    LaunchedEffect(historial.sesionCaducada) { if (historial.sesionCaducada) onSesionCaducada() }
    val descargar = rememberDescargaDocumento(flujo)
    val ocupado = historial.loading || flujo.loading

    ScreenScaffold(title = "Historial", subtitle = titulo, onAction = onBack, scrollable = true) {
        EstadoCargando(historial.loading)
        // Avisos y errores de descarga (FlujoViewModel).
        MensajesFlujo(flujo)
        historial.error?.let { msg -> EstadoError(msg, enabled = !historial.loading) { historial.cargar(localId) } }
        val vacio = if (historial.esGestion) historial.gestion.isEmpty() else historial.inquilino.isEmpty()
        if (historial.cargado && !historial.loading && historial.error == null && vacio) {
            EstadoVacio(
                if (historial.esGestion) "Este local no tiene historial de mantenimientos."
                else "No hay mantenimientos aprobados en tu ocupación de este local.",
                FhIcons.Historico
            )
        }
        if (historial.esGestion) {
            historial.gestion.forEach { h -> EntradaGestion(h, enabled = !ocupado) { id, nombre -> descargar(id, nombre) } }
        } else {
            historial.inquilino.forEach { h -> EntradaInquilino(h, enabled = !ocupado) { id, nombre -> descargar(id, nombre) } }
        }
    }
}

@Composable
private fun TarjetaHistorial(historico: Boolean = false, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (historico) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (historico) BorderStroke(1.dp, FhTema.semanticos.historico) else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)
    ) {
        Column(Modifier.padding(FhEspacio.m), content = contenido)
    }
}

@Composable
private fun EntradaInquilino(h: HistorialInquilinoDto, enabled: Boolean, onDescargar: (Long, String) -> Unit) {
    TarjetaHistorial {
        Text(h.tipoMantenimiento, style = MaterialTheme.typography.titleSmall)
        Text(
            listOfNotNull(h.instalacion, fechaCorta(h.cumplimiento?.fechaEjecucion ?: h.fechaRealizacion).takeIf { it.isNotBlank() })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        h.cumplimiento?.let { c -> CumplimientoCertificados(c, enabled) { cert -> onDescargar(cert.id, cert.nombre) } }
    }
}

@Composable
private fun EntradaGestion(h: HistorialGestionDto, enabled: Boolean, onDescargar: (Long, String) -> Unit) {
    // HISTORICO_P5 nunca se presenta como aprobado: tono propio, tarjeta atenuada con borde.
    val historico = h.tipoRegistro != "APROBADO" && h.tipoRegistro != "CERRADO_SIN_CUMPLIMIENTO"
    TarjetaHistorial(historico) {
        EstadoChip(h.etiqueta, tonoRegistroHistorial(h.tipoRegistro), iconoRegistroHistorial(h.tipoRegistro))
        Spacer(Modifier.height(FhEspacio.s))
        Text(h.tipoMantenimiento, style = MaterialTheme.typography.titleSmall)
        Text(listOfNotNull(h.instalacion, h.responsable?.let { "Responsable $it" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (h.intervencionId != null) {
            Text("Ejecución ${fechaCorta(h.fechaEjecucion)} · ${etiquetaResultado(h.resultado)} · ${etiquetaValidacion(h.estadoValidacion)}",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = FhEspacio.xs))
            h.proveedor?.let { Text("Proveedor: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            // Registro historico: se muestran su estado y fechas reales tal como constan.
            Text("Programado ${fechaCorta(h.fechaProgramada)}" + (h.fechaRealizacion?.let { " · realizado ${fechaCorta(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = FhEspacio.xs))
        }
        Text("Estado del mantenimiento: ${etiquetaEstadoInquilino(h.estadoMantenimiento.orEmpty(), historico)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        h.observaciones?.takeIf { it.isNotBlank() }?.let {
            Text("Observaciones: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        h.documentos.orEmpty().forEach { d ->
            FilaDocumento(
                nombre = d.nombreOriginal,
                chips = {
                    EstadoChip(etiquetaCategoria(d.categoria), if (d.categoria == "CERTIFICADO") Tono.PRIMARIO else Tono.NEUTRO)
                    d.estadoRevision?.let { EstadoChip(etiquetaRevision(it), tonoRevision(it)) }
                    EstadoChip(etiquetaVisibilidad(d.visibilidad), if (d.visibilidad == "COMPARTIDO") Tono.EXITO else Tono.NEUTRO)
                },
                accion = { TextButton(onClick = { onDescargar(d.id, d.nombreOriginal) }, enabled = enabled) { Text("Descargar") } }
            )
        }
    }
}
