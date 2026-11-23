package com.facilityhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
// FH-009 / R6: linea de tiempo vertical (LazyColumn) en el orden recibido de la API. Cada entrada es una ejecucion o
// registro real; las fechas se rotulan (ejecucion, programado, realizado) para no confundirlas. El historico P5 se
// distingue con punto atenuado, linea y borde discontinuos y su etiqueta del backend. Android no filtra ni reinterpreta.
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

    ScreenScaffold(title = "Historial", subtitle = titulo, onAction = onBack) {
        EstadoHistorial(historial, flujo, localId)
        LazyColumn(contentPadding = PaddingValues(bottom = FhEspacio.l)) {
            lineaTemporalHistorial(historial, enabled = !(historial.loading || flujo.loading), onDescargar = descargar)
        }
    }
}

/** Carga, error (con reintento), avisos de descarga y estado vacio del historial: fuera de la lista, siempre visibles. */
@Composable
internal fun EstadoHistorial(historial: HistorialViewModel, flujo: FlujoViewModel, localId: Long) {
    EstadoCargando(historial.loading)
    MensajesFlujo(flujo)
    historial.error?.let { msg -> EstadoError(msg, enabled = !historial.loading) { historial.cargar(localId, forzar = true) } }
    val vacio = if (historial.esGestion) historial.gestion.isEmpty() else historial.inquilino.isEmpty()
    if (historial.cargado && !historial.loading && historial.error == null && vacio) {
        EstadoVacio(
            if (historial.esGestion) "Este local todavía no tiene mantenimientos en el historial."
            else "Todavía no hay mantenimientos aprobados en el historial de tu local.",
            FhIcons.Historico
        )
    }
}

/** Entradas de la linea de tiempo (gestion o inquilino, segun lo que haya devuelto el backend). */
internal fun LazyListScope.lineaTemporalHistorial(
    historial: HistorialViewModel,
    enabled: Boolean,
    onDescargar: (Long, String) -> Unit
) {
    if (historial.esGestion) {
        val lista = historial.gestion
        itemsIndexed(lista, key = { i, h -> "g-${h.mantenimientoId}-${h.intervencionId}-$i" }) { i, h ->
            val p5 = h.tipoRegistro != "APROBADO" && h.tipoRegistro != "CERRADO_SIN_CUMPLIMIENTO"
            EntradaLineaTemporal(
                tono = if (p5) Tono.HISTORICO else tonoRegistroHistorial(h.tipoRegistro),
                atenuada = p5, primera = i == 0, ultima = i == lista.lastIndex
            ) { EntradaGestion(h, p5, enabled, onDescargar) }
        }
    } else {
        val lista = historial.inquilino
        itemsIndexed(lista, key = { i, h -> "i-${h.mantenimientoId}-$i" }) { i, h ->
            EntradaLineaTemporal(tono = Tono.EXITO, atenuada = false, primera = i == 0, ultima = i == lista.lastIndex) {
                EntradaInquilino(h, enabled, onDescargar)
            }
        }
    }
}

/**
 * Fila de la linea de tiempo: carril con punto y linea a la izquierda (dibujados detras, sin medidas intrinsecas) y
 * tarjeta a la derecha. Una entrada atenuada (historico P5) usa punto hueco, linea y borde discontinuos.
 */
@Composable
private fun EntradaLineaTemporal(
    tono: Tono,
    atenuada: Boolean,
    primera: Boolean,
    ultima: Boolean,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val colorPunto = if (atenuada) FhTema.semanticos.historico else coloresTono(tono).second
    val colorLinea = MaterialTheme.colorScheme.outlineVariant
    val colorBorde = FhTema.semanticos.historico
    val fondo = if (atenuada) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
    val discontinua = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 12.dp.toPx(); val yPunto = 28.dp.toPx(); val r = 7.dp.toPx()
                // Linea vertical que une las entradas (no sale por encima de la primera ni por debajo de la ultima).
                drawLine(colorLinea, Offset(x, if (primera) yPunto else 0f), Offset(x, if (ultima) yPunto else size.height),
                    strokeWidth = 2.dp.toPx(), pathEffect = if (atenuada) discontinua else null)
                if (atenuada) {
                    drawCircle(Color.Transparent, r, Offset(x, yPunto))
                    drawCircle(colorPunto, r, Offset(x, yPunto), style = Stroke(width = 2.dp.toPx(), pathEffect = discontinua))
                } else {
                    drawCircle(colorPunto, r, Offset(x, yPunto))
                }
            }
            .padding(start = 32.dp, bottom = FhEspacio.m)
    ) {
        Surface(
            color = fondo, shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (atenuada) drawRoundRect(colorBorde, cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = discontinua))
                }
                .semantics { if (atenuada) stateDescription = "Registro histórico sin validación" }
        ) {
            Column(Modifier.padding(FhEspacio.m), verticalArrangement = Arrangement.spacedBy(FhEspacio.xs), content = contenido)
        }
    }
}

@Composable
private fun EntradaInquilino(h: HistorialInquilinoDto, enabled: Boolean, onDescargar: (Long, String) -> Unit) {
    // El inquilino solo recibe cumplimientos aprobados y certificados compartidos: se presentan tal cual.
    val c = h.cumplimiento
    FilaChips { EstadoChip("Cumplimiento aprobado", Tono.EXITO, FhIcons.Verificado) }
    Text(h.tipoMantenimiento, style = MaterialTheme.typography.titleMedium)
    h.instalacion?.let { DatoEtiquetado("Instalación", it) }
    (c?.fechaEjecucion ?: h.fechaRealizacion)?.let { DatoEtiquetado("Ejecución", fechaCorta(it)) }
    c?.resultado?.let { DatoEtiquetado("Resultado", etiquetaResultado(it)) }
    if (c != null) {
        if (c.certificados.isNullOrEmpty()) {
            Text("Sin certificados compartidos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        c.certificados.orEmpty().forEach { cert ->
            FilaDocumento(
                nombre = cert.nombre,
                detalle = "Certificado compartido",
                accion = { BotonDescargar(cert.nombre, enabled) { onDescargar(cert.id, cert.nombre) } }
            )
        }
    }
}

@Composable
private fun EntradaGestion(h: HistorialGestionDto, p5: Boolean, enabled: Boolean, onDescargar: (Long, String) -> Unit) {
    // HISTORICO_P5 nunca se presenta como aprobado: etiqueta del backend y estilo atenuado.
    FilaChips { EstadoChip(h.etiqueta, tonoRegistroHistorial(h.tipoRegistro), iconoRegistroHistorial(h.tipoRegistro)) }
    Text(h.tipoMantenimiento, style = MaterialTheme.typography.titleMedium)
    h.instalacion?.let { DatoEtiquetado("Instalación", it) }
    if (h.intervencionId != null) {
        DatoEtiquetado("Ejecución", fechaCorta(h.fechaEjecucion).ifBlank { "—" })
        h.proveedor?.let { DatoEtiquetado("Proveedor", it) }
        DatoEtiquetado("Resultado", etiquetaResultado(h.resultado))
        DatoEtiquetado("Validación", etiquetaValidacion(h.estadoValidacion))
    } else {
        // Registro historico: estado y fechas reales tal como constan (programado y realizado, no ejecucion validada).
        DatoEtiquetado("Programado", fechaCorta(h.fechaProgramada).ifBlank { "—" })
        h.fechaRealizacion?.let { DatoEtiquetado("Realizado", fechaCorta(it)) }
    }
    h.responsable?.let { DatoEtiquetado("Responsable", it) }
    DatoEtiquetado("Estado", "${etiquetaEstadoInquilino(h.estadoMantenimiento.orEmpty(), p5)}")
    h.observaciones?.takeIf { it.isNotBlank() }?.let { DatoEtiquetado("Observaciones", it) }
    h.documentos.orEmpty().forEach { d ->
        FilaDocumento(
            nombre = d.nombreOriginal,
            chips = {
                EstadoChip(etiquetaCategoria(d.categoria), if (d.categoria == "CERTIFICADO") Tono.PRIMARIO else Tono.NEUTRO)
                d.estadoRevision?.let { EstadoChip(etiquetaRevision(it), tonoRevision(it)) }
                EstadoChip(etiquetaVisibilidad(d.visibilidad), if (d.visibilidad == "COMPARTIDO") Tono.EXITO else Tono.NEUTRO)
            },
            accion = { BotonDescargar(d.nombreOriginal, enabled) { onDescargar(d.id, d.nombreOriginal) } }
        )
    }
}
