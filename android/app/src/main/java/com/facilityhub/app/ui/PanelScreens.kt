package com.facilityhub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.ResumenGestionDto
import com.facilityhub.app.data.TrabajoGestionDto
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-006 / P6-D3-3: panel de Explotacion (ADMIN y EXPLOTACION). Siete indicadores del backend: los cinco primeros
// filtran el listado; declaraciones y solicitudes abren sus pantallas existentes. Solo lectura: las acciones siguen en
// las pantallas de destino (accionesPermitidas del backend).
// FH-009 / R1: indicadores con icono y tono semantico (direccion mixta). Textos e interaccion sin cambios.
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
        // La fecha del backend se muestra en ISO: la prueba automatica la busca literalmente ("Datos a AAAA-MM-DD").
        subtitle = r?.let { "Datos a ${it.fecha.orEmpty()} · preaviso ${it.preavisoDias} días" } ?: "Explotación",
        onAction = onBack
    ) {
        // Indicadores, cabecera y trabajos se desplazan juntos: con fuente grande los indicadores no dejan sin espacio a la lista.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(FhEspacio.s), modifier = Modifier.fillMaxWidth()) {
            item(key = "indicadores") {
                Indicadores(r, panel.filtro, enabled = !panel.loading, onFiltro = panel::seleccionar,
                    onDeclaraciones = onDeclaraciones, onSolicitudes = onSolicitudes)
            }
            item(key = "cabecera") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(panel.filtro.etiqueta, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { panel.cargar() }, enabled = !panel.loading) { Text("Actualizar") }
                    }
                    EstadoCargando(panel.loading)
                    panel.error?.let { msg -> EstadoError(msg, enabled = !panel.loading) { panel.cargar() } }
                    if (panel.cargado && !panel.loading && panel.error == null && panel.trabajos.isEmpty()) {
                        EstadoVacio("No hay trabajos en «${panel.filtro.etiqueta}».", FhIcons.Hecho)
                    }
                }
            }
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
        listOf(Indicador("Vencidos", valor(r?.vencidos), FiltroPanel.VENCIDOS, FhIcons.Vencido, Tono.ERROR),
            Indicador("Próximos", valor(r?.proximos), FiltroPanel.PROXIMOS, FhIcons.Calendario, Tono.AVISO)),
        listOf(Indicador("Pend. validación", valor(r?.pendientesValidacion), FiltroPanel.PENDIENTE_VALIDACION, FhIcons.Reloj, Tono.INFO),
            Indicador("Corrección", valor(r?.requierenCorreccion), FiltroPanel.REQUIERE_CORRECCION, FhIcons.Editar, Tono.ERROR)),
        listOf(Indicador("Sin asignar", valor(r?.sinAsignar), FiltroPanel.SIN_ASIGNAR, FhIcons.SinAsignar, Tono.NEUTRO),
            Indicador("Declarados", valor(r?.declaracionesPendientes), null, FhIcons.Bandeja, Tono.NEUTRO, abrir = onDeclaraciones)),
        listOf(Indicador("Solicitudes", valor(r?.solicitudesPendientes), null, FhIcons.Solicitud, Tono.NEUTRO, abrir = onSolicitudes))
    )
    Column(verticalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
        filas.forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(FhEspacio.s), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                fila.forEach { ind ->
                    TarjetaIndicador(ind, activo = ind.filtro != null && ind.filtro == seleccionado, enabled = enabled,
                        modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (ind.filtro != null) onFiltro(ind.filtro) else ind.abrir?.invoke()
                    }
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TarjetaIndicador(ind: Indicador, activo: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Los filtros usan su tono semantico; los accesos (Declarados, Solicitudes) se dibujan con borde, sin relleno.
    val acceso = ind.filtro == null
    val (fondo, contenido) = coloresTono(ind.tono)
    Surface(
        color = if (acceso) MaterialTheme.colorScheme.surface else fondo,
        contentColor = if (acceso) MaterialTheme.colorScheme.onSurface else contenido,
        shape = MaterialTheme.shapes.large,
        border = when {
            activo -> BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
            acceso -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            else -> null
        },
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .semantics { if (!acceso) selected = activo }
            .clickable(enabled = enabled, role = if (acceso) Role.Button else Role.Tab, onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = FhEspacio.m, vertical = FhEspacio.s)) {
            Icon(ind.icono, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(FhEspacio.m))
            Column {
                Text(ind.valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(ind.titulo + if (acceso) " ›" else "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class Indicador(
    val titulo: String,
    val valor: String,
    val filtro: FiltroPanel?,
    val icono: ImageVector,
    val tono: Tono,
    val abrir: (() -> Unit)? = null
)

@Composable
private fun FilaTrabajo(t: TrabajoGestionDto, onClick: () -> Unit) {
    val retraso = t.diasHastaVencimiento < 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Franja lateral de urgencia (color + texto: el retraso tambien se indica por escrito).
            Surface(color = if (retraso) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(4.dp).fillMaxHeight()) {}
            Column(Modifier.padding(FhEspacio.m).weight(1f)) {
                val dias: @Composable (Modifier) -> Unit = { m ->
                    Text(textoDias(t.diasHastaVencimiento), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = if (retraso) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = m)
                }
                // Con fuente grande (>= 150 %) el plazo se apila bajo el titulo para no partir palabras del titulo.
                if (LocalDensity.current.fontScale >= 1.5f) {
                    Text(t.tipoMantenimiento, style = MaterialTheme.typography.titleSmall)
                    dias(Modifier)
                } else {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(t.tipoMantenimiento, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        dias(Modifier.padding(start = FhEspacio.s))
                    }
                }
                Text(listOfNotNull(t.centro, if (t.zonaComun) "Zona común" else t.local).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilaChips {
                    etiquetaEstado(t.estado)?.let { EstadoChip(it, tonoEstado(t.estado)) }
                    t.estadoIntervencion?.let {
                        EstadoChip(etiquetaValidacion(it), tonoValidacion(it), iconoValidacion(it))
                    }
                    if (t.proveedorAsignado == null) EstadoChip("Sin proveedor", Tono.AVISO, FhIcons.SinAsignar)
                }
                Text(
                    listOfNotNull(t.proveedorAsignado, "Responsable ${t.responsable.orEmpty()}", "programado ${fechaCorta(t.fechaProgramada)}")
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = FhEspacio.xs)
                )
            }
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
