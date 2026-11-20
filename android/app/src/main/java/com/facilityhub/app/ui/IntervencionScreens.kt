package com.facilityhub.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.*

// FH-006 / P6-C3: intervenciones (PROVEEDOR y gestion) y validacion (ADMIN/EXPLOTACION). Cada boton aparece solo si
// la accion esta en accionesPermitidas; el backend vuelve a comprobarlo todo (403/404/409).

internal fun etiquetaResultado(r: String?) = when (r) {
    "CONFORME" -> "Conforme"
    "CON_INCIDENCIAS" -> "Con incidencias"
    "NO_REALIZADO" -> "No realizado"
    null -> "Sin resultado"
    else -> r
}

internal fun etiquetaValidacion(e: String?) = when (e) {
    "BORRADOR" -> "Borrador"
    "PENDIENTE_VALIDACION" -> "Pendiente de validación"
    "REQUIERE_CORRECCION" -> "Requiere corrección"
    "APROBADO" -> "Aprobada"
    "CERRADO_SIN_CUMPLIMIENTO" -> "Cerrada sin cumplimiento"
    null -> "Sin intervención"
    else -> e
}

internal fun fechaHora(valor: String?) = valor?.take(16)?.replace('T', ' ').orEmpty()

@Composable
internal fun MensajesFlujo(flujo: FlujoViewModel) {
    if (flujo.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    flujo.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp)) }
    flujo.aviso?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp)) }
}

// Lista de intervenciones de un mantenimiento (proveedor: solo las de su empresa; gestion: todas).
@Composable
internal fun ListaIntervenciones(intervenciones: List<IntervencionDto>, onIntervencion: (Long) -> Unit) {
    if (intervenciones.isEmpty()) {
        Text("Sin intervenciones registradas.", style = MaterialTheme.typography.bodySmall)
    }
    intervenciones.forEach { i ->
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onIntervencion(i.id) }) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("${etiquetaValidacion(i.estadoValidacion)} · envío ${i.numeroEnvio}", fontWeight = FontWeight.Bold)
                Text("${i.fechaEjecucion ?: "Sin fecha"} · ${etiquetaResultado(i.resultado)}")
                Text(listOfNotNull(i.proveedor, i.tecnico).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Seccion del detalle de trabajo del PROVEEDOR: intervenciones propias y registro si el backend lo permite.
@Composable
fun IntervencionesTrabajoSection(
    flujo: FlujoViewModel,
    trabajo: TrabajoProveedorDto,
    onIntervencion: (Long) -> Unit
) {
    LaunchedEffect(trabajo.mantenimientoId) { flujo.cargarIntervencionesMantenimiento(trabajo.mantenimientoId) }
    SectionTitle("Intervenciones")
    MensajesFlujo(flujo)
    if (trabajo.accionesPermitidas.orEmpty().contains(ACCION_REGISTRAR_INTERVENCION)) {
        Button(
            onClick = { flujo.registrarIntervencion(trabajo.mantenimientoId) { id -> onIntervencion(id) } },
            enabled = !flujo.loading
        ) { Text("Registrar intervención") }
    }
    ListaIntervenciones(flujo.intervencionesMantenimiento, onIntervencion)
}

@Composable
fun IntervencionScreen(
    flujo: FlujoViewModel,
    intervencionId: Long,
    onBack: () -> Unit
) {
    LaunchedEffect(intervencionId) { flujo.limpiarMensajes(); flujo.cargarIntervencion(intervencionId) }
    val i = flujo.intervencion?.takeIf { it.id == intervencionId }
    val acciones = i?.accionesPermitidas.orEmpty()

    // Formulario de borrador (se rellena con los datos cargados y se reinicia al cambiar de version).
    var fecha by remember(i?.id, i?.version) { mutableStateOf(i?.fechaEjecucion.orEmpty()) }
    var actuaciones by remember(i?.id, i?.version) { mutableStateOf(i?.actuaciones.orEmpty()) }
    var resultado by remember(i?.id, i?.version) { mutableStateOf(i?.resultado) }
    var incidencias by remember(i?.id, i?.version) { mutableStateOf(i?.incidencias.orEmpty()) }
    var categoria by remember { mutableStateOf("EVIDENCIA") }
    var confirmarEnvio by remember { mutableStateOf(false) }

    // Validacion
    var comentario by remember(i?.id, i?.version) { mutableStateOf("") }
    var compartir by remember(i?.id, i?.version) { mutableStateOf(setOf<Long>()) }
    var confirmar by remember { mutableStateOf<String?>(null) }

    var selectorAbierto by remember { mutableStateOf(false) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectorAbierto = false
        if (uri != null && i != null) flujo.adjuntar(i, categoria, uri)
    }
    var aDescargar by remember { mutableStateOf<DocumentoIntervencionDto?>(null) }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val d = aDescargar
        aDescargar = null
        if (uri != null && d != null) flujo.descargarDocumentoIntervencion(intervencionId, d.id, uri)
    }

    ScreenScaffold(title = "Intervención", subtitle = i?.let { etiquetaValidacion(it.estadoValidacion) } ?: "", onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        if (i == null) return@ScreenScaffold

        SectionTitle("Datos")
        Text("Proveedor: ${i.proveedor.orEmpty()}${i.tecnico?.let { " · $it" }.orEmpty()}")
        Text("Origen: ${if (i.origen == "REGISTRO_GESTION") "Registro de gestión" else "Proveedor"} · registrada por ${i.registradaPor.orEmpty()} el ${fechaHora(i.registradaAt)}")
        Text("Envío nº ${i.numeroEnvio}${i.enviadaAt?.let { " · enviada ${fechaHora(it)} por ${i.enviadaPor.orEmpty()}" }.orEmpty()}")
        if (i.ejecucionAnticipada) Text("Ejecución anticipada respecto a la fecha programada", style = MaterialTheme.typography.bodySmall)

        if (acciones.contains(ACCION_EDITAR_INTERVENCION)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha de ejecución o intento (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(actuaciones, { actuaciones = it }, label = { Text("Actuaciones realizadas") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text("Resultado", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("CONFORME", "CON_INCIDENCIAS", "NO_REALIZADO").forEach { r ->
                    FilterChip(selected = resultado == r, onClick = { resultado = r }, label = { Text(etiquetaResultado(r)) })
                }
            }
            OutlinedTextField(incidencias, { incidencias = it },
                label = { Text(if (resultado == "NO_REALIZADO") "Motivo del intento fallido (obligatorio)" else "Incidencias") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = { flujo.guardarBorrador(i, fecha, actuaciones, resultado, incidencias) }, enabled = !flujo.loading) {
                    Text("Guardar borrador")
                }
                if (acciones.contains(ACCION_ENVIAR_INTERVENCION)) {
                    Button(onClick = { confirmarEnvio = true }, enabled = !flujo.loading) { Text("Enviar a validación") }
                }
            }
            Text("Al enviar se guardan también los cambios del formulario.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Fecha: ${i.fechaEjecucion ?: "—"} · Resultado: ${etiquetaResultado(i.resultado)}")
            i.actuaciones?.let { Text("Actuaciones: $it") }
            i.incidencias?.let { Text("Incidencias / motivo: $it") }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Documentos")
        if (acciones.contains(ACCION_ADJUNTAR_DOCUMENTO)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("EVIDENCIA" to "Foto/evidencia", "CERTIFICADO" to "Certificado", "OTRO" to "Parte/otro").forEach { (c, t) ->
                    FilterChip(selected = categoria == c, onClick = { categoria = c }, label = { Text(t) })
                }
            }
            Button(
                onClick = { if (!selectorAbierto) { selectorAbierto = true; selector.launch(TIPOS_DOCUMENTO) } },
                enabled = !flujo.loading && !selectorAbierto
            ) { Text("Adjuntar archivo") }
            Text("PDF, JPEG o PNG · máximo 20 MB", style = MaterialTheme.typography.bodySmall)
        }
        if (i.documentos.isNullOrEmpty()) Text("Sin documentos.", style = MaterialTheme.typography.bodySmall)
        i.documentos.orEmpty().forEach { d ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.nombreOriginal, fontWeight = FontWeight.Bold)
                        // La visibilidad se muestra tal cual: un documento INTERNO nunca se presenta como compartido.
                        Text("${d.categoria ?: "—"} · ${d.estadoRevision} · ${if (d.visibilidad == "COMPARTIDO") "Compartido con el inquilino" else "Interno"}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { aDescargar = d; guardar.launch(d.nombreOriginal) }, enabled = !flujo.loading) { Text("Descargar") }
                }
                if (acciones.contains(ACCION_APROBAR) && d.categoria == "CERTIFICADO") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                        Checkbox(checked = d.id in compartir, onCheckedChange = { marcado -> compartir = if (marcado) compartir + d.id else compartir - d.id })
                        Text("Compartir con el inquilino al aprobar", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (!i.validaciones.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Historial de validación")
            i.validaciones.orEmpty().forEach { v ->
                Text("${fechaHora(v.fecha)} · envío ${v.numeroEnvio} · ${v.accion} · ${v.validadaPor.orEmpty()}", fontWeight = FontWeight.Bold)
                v.comentario?.let { Text(it) }
            }
        }

        val accionesValidacion = listOf(ACCION_APROBAR, ACCION_SOLICITAR_CORRECCION, ACCION_CERRAR_SIN_CUMPLIMIENTO).filter { it in acciones }
        if (accionesValidacion.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Validación")
            OutlinedTextField(comentario, { comentario = it }, label = { Text("Comentario (obligatorio para corregir o cerrar)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (ACCION_APROBAR in accionesValidacion) {
                Text("Los certificados solo se comparten si los marcas; el resto queda interno.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (ACCION_APROBAR in accionesValidacion) Button(onClick = { confirmar = ACCION_APROBAR }, enabled = !flujo.loading) { Text("Aprobar") }
                if (ACCION_SOLICITAR_CORRECCION in accionesValidacion) OutlinedButton(
                    onClick = { confirmar = ACCION_SOLICITAR_CORRECCION }, enabled = !flujo.loading && comentario.isNotBlank()) { Text("Pedir corrección") }
            }
            if (ACCION_CERRAR_SIN_CUMPLIMIENTO in accionesValidacion) OutlinedButton(
                onClick = { confirmar = ACCION_CERRAR_SIN_CUMPLIMIENTO }, enabled = !flujo.loading && comentario.isNotBlank()) { Text("Cerrar sin cumplimiento") }
        }
    }

    if (confirmarEnvio && i != null) {
        AlertDialog(
            onDismissRequest = { confirmarEnvio = false },
            title = { Text("Enviar a validación") },
            text = { Text("Se guardarán los datos del formulario y se enviarán. Después no podrás modificarla salvo que Explotación pida una corrección.") },
            confirmButton = { TextButton(onClick = { confirmarEnvio = false; flujo.guardarYEnviar(i, fecha, actuaciones, resultado, incidencias) }) { Text("Enviar") } },
            dismissButton = { TextButton(onClick = { confirmarEnvio = false }) { Text("Cancelar") } }
        )
    }
    confirmar?.let { accion ->
        if (i != null) AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(when (accion) { ACCION_APROBAR -> "Aprobar intervención"; ACCION_CERRAR_SIN_CUMPLIMIENTO -> "Cerrar sin cumplimiento"; else -> "Pedir corrección" }) },
            text = {
                Text(when (accion) {
                    ACCION_APROBAR -> "Se declarará el cumplimiento y se recalculará la próxima fecha. Certificados a compartir: ${compartir.size}."
                    ACCION_CERRAR_SIN_CUMPLIMIENTO -> "El intento queda registrado sin cumplimiento; no cambia la próxima fecha y el mantenimiento sigue pendiente."
                    else -> "El proveedor podrá modificar y reenviar la intervención."
                })
            },
            confirmButton = { TextButton(onClick = { confirmar = null; flujo.validar(i, accion, comentario, compartir.toList()) }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }
        )
    }
}

// Gestion: detalle operativo de un mantenimiento (asignacion vigente e intervenciones). Sustituye al antiguo "Finalizar".
@Composable
fun MantenimientoGestionScreen(
    flujo: FlujoViewModel,
    mantenimientoId: Long,
    titulo: String,
    onIntervencion: (Long) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(mantenimientoId) {
        flujo.limpiarMensajes(); flujo.cargarAsignacion(mantenimientoId); flujo.cargarIntervencionesMantenimiento(mantenimientoId)
    }
    ScreenScaffold(title = "Intervenciones", subtitle = titulo, onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        SectionTitle("Asignación")
        val a = flujo.asignacion
        if (a != null) {
            Text("Proveedor: ${a.proveedor}")
            a.tecnico?.let { Text("Técnico: $it") }
            Text("Asignado: ${fechaHora(a.asignadoAt)}", style = MaterialTheme.typography.bodySmall)
        } else if (flujo.asignacionConsultada) {
            Text("Sin proveedor asignado.")
        }
        Text("El cumplimiento se declara aprobando una intervención presentada; no hay finalización manual.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        SectionTitle("Intervenciones")
        ListaIntervenciones(flujo.intervencionesMantenimiento, onIntervencion)
    }
}

@Composable
fun BandejaValidacionScreen(flujo: FlujoViewModel, onIntervencion: (Long) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { flujo.limpiarMensajes(); flujo.cargarPendientes() }
    ScreenScaffold(title = "Pendientes de validación", subtitle = "Explotación", onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        if (!flujo.loading && flujo.pendientes.isEmpty() && flujo.error == null) Text("No hay intervenciones pendientes.")
        flujo.pendientes.forEach { p ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onIntervencion(p.id) }) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${p.tipoMantenimiento} · ${etiquetaResultado(p.resultado)}", fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(p.centro, p.local ?: "Zona común", p.proveedor).joinToString(" · "))
                    Text("Ejecución ${p.fechaEjecucion ?: "—"} · envío ${p.numeroEnvio} · responsable ${p.responsable}" +
                        if (p.ejecucionAnticipada) " · anticipada" else "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ProveedoresScreen(flujo: FlujoViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { flujo.limpiarMensajes(); flujo.cargarProveedores() }
    ScreenScaffold(title = "Proveedores", subtitle = "Consulta", onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        flujo.proveedores.forEach { p ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(p.razonSocial, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(p.cif, if (p.activo) "Activo" else "De baja").joinToString(" · "))
                    Text("Técnicos: ${p.tecnicos.orEmpty().count { it.activo }} activos", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
