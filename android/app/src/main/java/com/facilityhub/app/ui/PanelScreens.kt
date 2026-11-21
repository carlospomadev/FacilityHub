package com.facilityhub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.ResumenGestionDto
import com.facilityhub.app.data.TrabajoGestionDto

// FH-006 / P6-D3-3: panel de Explotacion (ADMIN y EXPLOTACION). Siete indicadores del backend: los cinco primeros
// filtran el listado; declaraciones y solicitudes abren sus pantallas existentes. Solo lectura: las acciones siguen en
// las pantallas de destino (accionesPermitidas del backend).
@Composable
fun PanelGestionScreen(
    panel: PanelViewModel,
    onMantenimiento: (TrabajoGestionDto) -> Unit,
    onIntervencion: (Long) -> Unit,
    onDeclaraciones: () -> Unit,
    onSolicitudes: () -> Unit,
    onSesionCaducada: () -> Unit,
    onBack: () -> Unit
) {
    // Se recarga al abrir el panel y cada vez que se vuelve a el (el filtro elegido se conserva en el ViewModel).
    LaunchedEffect(Unit) { panel.cargar() }
    LaunchedEffect(panel.sesionCaducada) { if (panel.sesionCaducada) onSesionCaducada() }
    val r = panel.resumen

    ScreenScaffold(
        title = "Panel de Explotación",
        subtitle = r?.let { "Datos a ${it.fecha.orEmpty()} · preaviso ${it.preavisoDias} días" } ?: "Explotación",
        onAction = onBack
    ) {
        Indicadores(r, panel.filtro, enabled = !panel.loading, onFiltro = panel::seleccionar,
            onDeclaraciones = onDeclaraciones, onSolicitudes = onSolicitudes)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(panel.filtro.etiqueta, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { panel.cargar() }, enabled = !panel.loading) { Text("Actualizar") }
        }
        if (panel.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        panel.error?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { panel.cargar() }, enabled = !panel.loading) { Text("Reintentar") }
                }
            }
        }
        if (panel.cargado && !panel.loading && panel.error == null && panel.trabajos.isEmpty()) {
            Text("No hay trabajos en «${panel.filtro.etiqueta}».", modifier = Modifier.padding(vertical = 8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(panel.trabajos, key = { "${it.mantenimientoId}-${it.intervencionId}" }) { t ->
                FilaTrabajo(t) {
                    // Las bandejas de validacion abren la intervencion; el resto, la gestion del mantenimiento.
                    // El destino vuelve a validar el acceso contra el backend.
                    val id = t.intervencionId
                    if (id != null && (panel.filtro == FiltroPanel.PENDIENTE_VALIDACION || panel.filtro == FiltroPanel.REQUIERE_CORRECCION)) {
                        onIntervencion(id)
                    } else {
                        onMantenimiento(t)
                    }
                }
            }
        }
    }
}

@Composable
private fun Indicadores(
    r: ResumenGestionDto?,
    seleccionado: FiltroPanel,
    enabled: Boolean,
    onFiltro: (FiltroPanel) -> Unit,
    onDeclaraciones: () -> Unit,
    onSolicitudes: () -> Unit
) {
    val valor: (Int?) -> String = { it?.toString() ?: "–" }
    val filas = listOf(
        listOf(Indicador("Vencidos", valor(r?.vencidos), FiltroPanel.VENCIDOS, alerta = (r?.vencidos ?: 0) > 0),
            Indicador("Próximos", valor(r?.proximos), FiltroPanel.PROXIMOS)),
        listOf(Indicador("Pend. validación", valor(r?.pendientesValidacion), FiltroPanel.PENDIENTE_VALIDACION),
            Indicador("Corrección", valor(r?.requierenCorreccion), FiltroPanel.REQUIERE_CORRECCION)),
        listOf(Indicador("Sin asignar", valor(r?.sinAsignar), FiltroPanel.SIN_ASIGNAR),
            Indicador("Declarados", valor(r?.declaracionesPendientes), null, abrir = onDeclaraciones)),
        listOf(Indicador("Solicitudes", valor(r?.solicitudesPendientes), null, abrir = onSolicitudes))
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        filas.forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                fila.forEach { ind ->
                    val activo = ind.filtro != null && ind.filtro == seleccionado
                    OutlinedCard(
                        modifier = Modifier.weight(1f).clickable(enabled = enabled) {
                            if (ind.filtro != null) onFiltro(ind.filtro) else ind.abrir?.invoke()
                        },
                        border = BorderStroke(if (activo) 2.dp else 1.dp,
                            if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (activo) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(ind.valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = if (ind.alerta) MaterialTheme.colorScheme.error else Color.Unspecified)
                            Text(ind.titulo + if (ind.filtro == null) " ›" else "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class Indicador(
    val titulo: String,
    val valor: String,
    val filtro: FiltroPanel?,
    val alerta: Boolean = false,
    val abrir: (() -> Unit)? = null
)

@Composable
private fun FilaTrabajo(t: TrabajoGestionDto, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(t.tipoMantenimiento, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(t.centro, if (t.zonaComun) "Zona común" else t.local).joinToString(" · "))
            Text("Responsable ${t.responsable.orEmpty()} · programado ${t.fechaProgramada.orEmpty()}",
                style = MaterialTheme.typography.bodySmall)
            Text(listOfNotNull(etiquetaEstado(t.estado), t.estadoIntervencion?.let { "intervención: " + etiquetaValidacion(it) })
                .joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Text("Proveedor: ${t.proveedorAsignado ?: "sin asignar"}", style = MaterialTheme.typography.bodySmall)
            val retraso = t.diasHastaVencimiento < 0
            Text(textoDias(t.diasHastaVencimiento), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                color = if (retraso) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}

// Presentacion del valor calculado por el backend (no se recalcula).
internal fun textoDias(dias: Long) = when {
    dias < 0 -> "${-dias} ${if (dias == -1L) "día" else "días"} de retraso"
    dias == 0L -> "Vence hoy"
    else -> "Vence en $dias ${if (dias == 1L) "día" else "días"}"
}

internal fun etiquetaEstado(e: String?) = when (e) {
    "PROGRAMADO" -> "Programado"
    "PENDIENTE" -> "Pendiente"
    "EN_PROCESO" -> "En proceso"
    "FINALIZADO" -> "Finalizado (enviado)"
    "CANCELADO" -> "Cancelado"
    null -> null
    else -> e
}
