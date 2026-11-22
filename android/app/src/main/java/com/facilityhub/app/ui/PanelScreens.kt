package com.facilityhub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.ResumenGestionDto
import com.facilityhub.app.data.TrabajoGestionDto
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-006 / P6-D3-3: panel de Explotacion (ADMIN y EXPLOTACION). Contadores y listados calculados por el backend
// (resumen y trabajos de gestion); Android no recalcula estados, vencimientos ni cifras. Solo lectura: las acciones
// siguen en las pantallas de destino (accionesPermitidas del backend).
// FH-009 / R4: indicadores con icono y tono semantico (la seleccion se marca tambien sin color: borde, icono de
// verificacion y estado accesible), accesos «Declarados» y «Solicitudes» separados de los filtros, TrabajoCard, deslizar
// para actualizar y fecha de los datos legible dentro del contenido (sin truncarse en la barra superior).
@OptIn(ExperimentalMaterial3Api::class)
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
    // Se recarga al abrir el panel y cada vez que se vuelve a el (el filtro elegido se conserva en el ViewModel y la
    // lista visible no se borra mientras llegan los datos nuevos).
    LaunchedEffect(Unit) { panel.cargar() }
    LaunchedEffect(panel.sesionCaducada) { if (panel.sesionCaducada) onSesionCaducada() }
    val r = panel.resumen
    val abreIntervencion = panel.filtro == FiltroPanel.PENDIENTE_VALIDACION || panel.filtro == FiltroPanel.REQUIERE_CORRECCION
    val lista = rememberLazyListState()

    ScreenScaffold(title = "Panel de Explotación", subtitle = "", onAction = onBack) {
        PullToRefreshBox(
            isRefreshing = panel.refrescando,
            onRefresh = { panel.refrescar() },
            modifier = Modifier.fillMaxSize()
        ) {
            // Un unico contenido desplazable: con fuente grande los indicadores no dejan sin espacio a la lista.
            LazyColumn(
                state = lista,
                verticalArrangement = Arrangement.spacedBy(FhEspacio.s),
                contentPadding = PaddingValues(bottom = FhEspacio.l),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "fecha") {
                    // La fecha la calcula el backend (Europe/Madrid); aqui solo se presenta legible.
                    Text(
                        r?.let { "Datos a ${fechaCorta(it.fecha)} · preaviso de ${it.preavisoDias} días" } ?: "Explotación",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item(key = "indicadores") {
                    Indicadores(r, panel.filtro, enabled = !panel.loading, onFiltro = panel::seleccionar)
                }
                item(key = "accesos") {
                    Column(verticalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
                        SectionTitle("Otras bandejas")
                        AccesoBandeja("Declarados", "Proveedores declarados por inquilinos", r?.declaracionesPendientes,
                            FhIcons.Bandeja, onDeclaraciones)
                        AccesoBandeja("Solicitudes", "Solicitudes de proveedor nuevo", r?.solicitudesPendientes,
                            FhIcons.Solicitud, onSolicitudes)
                    }
                }
                item(key = "cabecera") {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = FhEspacio.s)) {
                            Text(
                                panel.filtro.etiqueta + if (panel.cargado) " (${panel.trabajos.size})" else "",
                                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { panel.refrescar() }, enabled = !panel.loading) { Text("Actualizar") }
                        }
                        // Carga inicial o cambio de filtro (sin datos que mostrar); una recarga usa el indicador de deslizar.
                        EstadoCargando(panel.loading && !panel.refrescando)
                        panel.error?.let { msg -> EstadoError(msg, enabled = !panel.loading) { panel.refrescar() } }
                        if (panel.cargado && !panel.loading && panel.error == null && panel.trabajos.isEmpty()) {
                            EstadoVacio("No hay trabajos en «${panel.filtro.etiqueta}».", FhIcons.Hecho)
                        }
                    }
                }
                items(panel.trabajos, key = { "${it.mantenimientoId}-${it.intervencionId}" }) { t ->
                    // Las bandejas de validacion abren la intervencion; el resto, la gestion del mantenimiento.
                    // El destino vuelve a validar el acceso contra el backend.
                    val id = t.intervencionId
                    val intervencion = id != null && abreIntervencion
                    TrabajoCard(t, preavisoDias = r?.preavisoDias, accion = if (intervencion) "Abrir intervención" else "Abrir mantenimiento") {
                        if (intervencion) onIntervencion(id!!) else onMantenimiento(t)
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
    onFiltro: (FiltroPanel) -> Unit
) {
    val valor: (Int?) -> String = { it?.toString() ?: "–" }
    val filas = listOf(
        listOf(Indicador("Vencidos", valor(r?.vencidos), FiltroPanel.VENCIDOS, FhIcons.Vencido, Tono.ERROR),
            Indicador("Próximos", valor(r?.proximos), FiltroPanel.PROXIMOS, FhIcons.Calendario, Tono.AVISO)),
        listOf(Indicador("Pend. validación", valor(r?.pendientesValidacion), FiltroPanel.PENDIENTE_VALIDACION, FhIcons.Reloj, Tono.INFO),
            Indicador("Corrección", valor(r?.requierenCorreccion), FiltroPanel.REQUIERE_CORRECCION, FhIcons.Editar, Tono.ERROR)),
        listOf(Indicador("Sin asignar", valor(r?.sinAsignar), FiltroPanel.SIN_ASIGNAR, FhIcons.SinAsignar, Tono.NEUTRO))
    )
    Column(verticalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
        filas.forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(FhEspacio.s), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                fila.forEach { ind ->
                    TarjetaIndicador(ind, activo = ind.filtro == seleccionado, enabled = enabled,
                        modifier = Modifier.weight(1f).fillMaxHeight()) { onFiltro(ind.filtro) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaIndicador(ind: Indicador, activo: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (fondo, contenido) = coloresTono(ind.tono)
    Surface(
        color = fondo,
        contentColor = contenido,
        shape = MaterialTheme.shapes.large,
        // Seleccion reconocible sin depender del color: borde grueso e icono de verificacion.
        border = if (activo) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .semantics {
                selected = activo
                stateDescription = if (activo) "Filtro activo" else "Filtro no activo"
            }
            .clickable(enabled = enabled, role = Role.Tab, onClickLabel = "Filtrar por ${ind.titulo}", onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = FhEspacio.m, vertical = FhEspacio.s)) {
            Icon(ind.icono, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(FhEspacio.m))
            Column(Modifier.weight(1f)) {
                Text(ind.valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(ind.titulo, style = MaterialTheme.typography.bodySmall, fontWeight = if (activo) FontWeight.Bold else null)
            }
            if (activo) Icon(FhIcons.Hecho, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

private data class Indicador(
    val titulo: String,
    val valor: String,
    val filtro: FiltroPanel,
    val icono: ImageVector,
    val tono: Tono
)

/** Acceso a otra pantalla (no filtra la lista): fila con icono, nombre, contador del backend y flecha. */
@Composable
private fun AccesoBandeja(titulo: String, detalle: String, contador: Int?, icono: ImageVector, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = "Abrir $titulo", onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = FhEspacio.m, vertical = FhEspacio.s)) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(FhEspacio.m))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (contador != null) {
                Text(
                    contador.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = FhEspacio.s)
                        .semantics { contentDescription = "$contador pendientes" }
                )
            }
            Icon(FhIcons.Siguiente, contentDescription = null)
        }
    }
}

/**
 * FH-009 / R4: tarjeta de mantenimiento del panel. Informacion principal: nombre; debajo, plazo (retraso o dias
 * restantes, calculado por el backend), ubicacion, estados con EstadoChip, proveedor y fecha prevista. La franja lateral
 * y el plazo marcan de inmediato los vencidos y los que requieren correccion (con texto, no solo color).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrabajoCard(t: TrabajoGestionDto, preavisoDias: Int?, accion: String, onClick: () -> Unit) {
    val vencido = t.diasHastaVencimiento < 0
    val correccion = t.estadoIntervencion == "REQUIERE_CORRECCION"
    val proximo = !vencido && preavisoDias != null && t.diasHastaVencimiento <= preavisoDias
    val tonoPlazo = when {
        vencido -> Tono.ERROR
        proximo -> Tono.AVISO
        else -> Tono.NEUTRO
    }
    val franja = when {
        vencido || correccion -> MaterialTheme.colorScheme.error
        t.estadoIntervencion == "PENDIENTE_VALIDACION" -> coloresTono(Tono.INFO).second
        proximo -> coloresTono(Tono.AVISO).second
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = accion, role = Role.Button, onClick = onClick)
    ) {
        // La franja lateral se dibuja detras del contenido: sin medidas intrinsecas, que con los chips en FlowRow
        // calculaban una altura menor que la real y recortaban las ultimas lineas con fuente grande.
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(franja, size = Size(6.dp.toPx(), size.height)) }
                .padding(start = 6.dp + FhEspacio.m, end = FhEspacio.m, top = FhEspacio.m, bottom = FhEspacio.m),
            verticalArrangement = Arrangement.spacedBy(FhEspacio.xs)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(t.tipoMantenimiento, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(FhIcons.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Plazo en una linea propia: no compite en anchura con el titulo (fuente al 200 %).
            EstadoChip(textoDias(t.diasHastaVencimiento), tonoPlazo,
                if (vencido) FhIcons.Vencido else FhIcons.Calendario)
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
                listOfNotNull(
                    t.proveedorAsignado?.let { "Proveedor: $it" },
                    t.responsable?.let { "Responsable: $it" },
                    "Fecha prevista: ${fechaCorta(t.fechaProgramada)}"
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    // FH-009 / R4: un estado nuevo del backend no se muestra como codigo interno (EN_ESPERA -> «En espera»).
    else -> e.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
