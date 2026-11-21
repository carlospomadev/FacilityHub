package com.facilityhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.HistorialGestionDto
import com.facilityhub.app.data.HistorialInquilinoDto

// FH-006 / P6-D3-4: historial de mantenimientos del local. Contenido decidido por el backend segun el rol:
//  - gestion: aprobados, cierres sin cumplimiento e historicos P5 ("Historico P5 — sin validacion", con su estado real);
//  - INQUILINO: solo cumplimientos aprobados de su ocupacion vigente con certificados VALIDO + COMPARTIDO.
// Las descargas pasan por GET /api/documentos/{id}/archivo, que vuelve a comprobar la autorizacion.
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
        if (historial.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        // Avisos y errores de descarga (FlujoViewModel).
        MensajesFlujo(flujo)
        historial.error?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { historial.cargar(localId) }, enabled = !historial.loading) { Text("Reintentar") }
                }
            }
        }
        val vacio = if (historial.esGestion) historial.gestion.isEmpty() else historial.inquilino.isEmpty()
        if (historial.cargado && !historial.loading && historial.error == null && vacio) {
            Text(if (historial.esGestion) "Este local no tiene historial de mantenimientos."
                 else "No hay mantenimientos aprobados en tu ocupación de este local.",
                modifier = Modifier.padding(vertical = 8.dp))
        }
        if (historial.esGestion) {
            historial.gestion.forEach { h -> EntradaGestion(h, enabled = !ocupado) { id, nombre -> descargar(id, nombre) } }
        } else {
            historial.inquilino.forEach { h -> EntradaInquilino(h, enabled = !ocupado) { id, nombre -> descargar(id, nombre) } }
        }
    }
}

@Composable
private fun EntradaInquilino(h: HistorialInquilinoDto, enabled: Boolean, onDescargar: (Long, String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${h.cumplimiento?.fechaEjecucion ?: h.fechaRealizacion.orEmpty()} · ${h.tipoMantenimiento}", fontWeight = FontWeight.Bold)
            h.instalacion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            h.cumplimiento?.let { c -> CumplimientoCertificados(c, enabled) { cert -> onDescargar(cert.id, cert.nombre) } }
        }
    }
}

@Composable
private fun EntradaGestion(h: HistorialGestionDto, enabled: Boolean, onDescargar: (Long, String) -> Unit) {
    val color = when (h.tipoRegistro) {
        "APROBADO" -> MaterialTheme.colorScheme.primary
        "CERRADO_SIN_CUMPLIMIENTO" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary // HISTORICO_P5: nunca se presenta como aprobado
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(h.etiqueta, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(h.tipoMantenimiento, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(h.instalacion, h.responsable?.let { "Responsable $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall)
            if (h.intervencionId != null) {
                Text("Ejecución ${h.fechaEjecucion.orEmpty()} · ${etiquetaResultado(h.resultado)} · ${etiquetaValidacion(h.estadoValidacion)}")
                h.proveedor?.let { Text("Proveedor: $it", style = MaterialTheme.typography.bodySmall) }
            } else {
                // Registro historico: se muestran su estado y fechas reales tal como constan.
                Text("Programado ${h.fechaProgramada.orEmpty()}" + (h.fechaRealizacion?.let { " · realizado $it" } ?: ""))
            }
            Text("Estado registrado del mantenimiento: ${h.estadoMantenimiento.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            h.observaciones?.takeIf { it.isNotBlank() }?.let { Text("Observaciones: $it", style = MaterialTheme.typography.bodySmall) }
            h.documentos.orEmpty().forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.nombreOriginal)
                        Text(listOfNotNull(d.categoria, d.estadoRevision, d.visibilidad).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onDescargar(d.id, d.nombreOriginal) }, enabled = enabled) { Text("Descargar") }
                }
            }
        }
    }
}
