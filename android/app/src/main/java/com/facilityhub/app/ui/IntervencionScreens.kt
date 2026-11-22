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
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-006 / P6-C3: intervenciones (PROVEEDOR y gestion) y validacion (ADMIN/EXPLOTACION). Cada boton aparece solo si
// la accion esta en accionesPermitidas; el backend vuelve a comprobarlo todo (403/404/409).

internal fun etiquetaResultado(r: String?) = when (r) {
    "CONFORME" -> "Conforme"
    "CON_INCIDENCIAS" -> "Con incidencias"
    "NO_REALIZADO" -> "No realizado"
    null -> "Sin resultado"
    else -> r
}

internal fun etiquetaAccionValidacion(a: String?) = when (a) {
    "APROBAR" -> "Aprobada"
    "SOLICITAR_CORRECCION" -> "Corrección solicitada"
    "CERRAR_SIN_CUMPLIMIENTO" -> "Cerrada sin cumplimiento"
    null -> ""
    else -> a.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
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

@Composable
internal fun MensajesFlujo(flujo: FlujoViewModel) {
    EstadoCargando(flujo.loading)
    flujo.error?.let { EstadoError(it) }
    flujo.aviso?.let { Mensaje(it, Tono.EXITO) }
}

// Lista de intervenciones de un mantenimiento (proveedor: solo las de su empresa; gestion: todas).
@Composable
internal fun ListaIntervenciones(intervenciones: List<IntervencionDto>, onIntervencion: (Long) -> Unit) {
    if (intervenciones.isEmpty()) {
        EstadoVacio("Sin intervenciones registradas.", FhIcons.Herramienta)
    }
    intervenciones.forEach { i ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs).clickable { onIntervencion(i.id) }
        ) {
            Column(modifier = Modifier.padding(FhEspacio.m)) {
                FilaChips(Modifier.padding(bottom = FhEspacio.xs)) {
                    EstadoChip(etiquetaValidacion(i.estadoValidacion), tonoValidacion(i.estadoValidacion), iconoValidacion(i.estadoValidacion))
                }
                Text("${i.fechaEjecucion?.let { fechaCorta(it) } ?: "Sin fecha"} · ${etiquetaResultado(i.resultado)} · envío ${i.numeroEnvio}",
                    style = MaterialTheme.typography.bodyMedium)
                Text(listOfNotNull(i.proveedor, i.tecnico).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        FilaChips(Modifier.padding(bottom = FhEspacio.s)) {
            EstadoChip(etiquetaValidacion(i.estadoValidacion), tonoValidacion(i.estadoValidacion), iconoValidacion(i.estadoValidacion))
            if (i.resultado != null) EstadoChip(etiquetaResultado(i.resultado),
                if (i.resultado == "CONFORME") Tono.EXITO else Tono.AVISO, if (i.resultado == "CONFORME") FhIcons.Hecho else null)
            if (i.ejecucionAnticipada) EstadoChip("Ejecución anticipada", Tono.NEUTRO)
        }
        SectionTitle("Datos")
        DatoEtiquetado("Proveedor", i.proveedor.orEmpty())
        i.tecnico?.let { DatoEtiquetado("Técnico", it) }
        DatoEtiquetado("Origen", (if (i.origen == "REGISTRO_GESTION") "Registro de gestión" else "Proveedor") +
            " · ${i.registradaPor.orEmpty()}, ${fechaHora(i.registradaAt)}")
        DatoEtiquetado("Envío", "nº ${i.numeroEnvio}${i.enviadaAt?.let { " · ${fechaHora(it)} por ${i.enviadaPor.orEmpty()}" }.orEmpty()}")

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
            DatoEtiquetado("Ejecución", i.fechaEjecucion?.let { fechaCorta(it) } ?: "—")
            DatoEtiquetado("Resultado", etiquetaResultado(i.resultado))
            i.actuaciones?.let { DatoEtiquetado("Actuaciones", it) }
            i.incidencias?.let { DatoEtiquetado("Incidencias", it) }
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
        if (i.documentos.isNullOrEmpty()) EstadoVacio("Sin documentos.", FhIcons.Documento)
        i.documentos.orEmpty().forEach { d ->
            // La visibilidad se muestra tal cual: un documento INTERNO nunca se presenta como compartido.
            FilaDocumento(
                nombre = d.nombreOriginal,
                chips = {
                    EstadoChip(etiquetaCategoria(d.categoria), if (d.categoria == "CERTIFICADO") Tono.PRIMARIO else Tono.NEUTRO)
                    EstadoChip(etiquetaRevision(d.estadoRevision), tonoRevision(d.estadoRevision))
                    EstadoChip(etiquetaVisibilidad(d.visibilidad), if (d.visibilidad == "COMPARTIDO") Tono.EXITO else Tono.NEUTRO)
                },
                accion = { TextButton(onClick = { aDescargar = d; guardar.launch(d.nombreOriginal) }, enabled = !flujo.loading) { Text("Descargar") } }
            ) {
                if (acciones.contains(ACCION_APROBAR) && d.categoria == "CERTIFICADO") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                Column(Modifier.padding(vertical = FhEspacio.xs)) {
                    Text("${etiquetaAccionValidacion(v.accion)} · envío ${v.numeroEnvio}", style = MaterialTheme.typography.titleSmall)
                    Text("${fechaHora(v.fecha)} · ${v.validadaPor.orEmpty()}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    v.comentario?.let { Text("«$it»", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        val accionesValidacion = listOf(ACCION_APROBAR, ACCION_SOLICITAR_CORRECCION, ACCION_CERRAR_SIN_CUMPLIMIENTO).filter { it in acciones }
        if (accionesValidacion.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Validación")
            OutlinedTextField(comentario, { comentario = it }, label = { Text("Comentario (obligatorio para corregir o cerrar)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (ACCION_APROBAR in accionesValidacion) {
                Text("Los certificados solo se comparten si los marcas; el resto queda interno.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ACCION_SOLICITAR_CORRECCION in accionesValidacion && comentario.isBlank()) {
                Text("Para pedir corrección o cerrar, escribe un comentario.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        flujo.cargarProveedores()
    }
    var proveedorElegido by remember(mantenimientoId) { mutableStateOf<ProveedorDto?>(null) }
    var confirmarAsignacion by remember { mutableStateOf(false) }
    ScreenScaffold(title = "Intervenciones", subtitle = titulo, onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        SectionTitle("Asignación")
        val a = flujo.asignacion
        if (a != null) {
            DatoEtiquetado("Proveedor", a.proveedor)
            a.tecnico?.let { DatoEtiquetado("Técnico", it) }
            DatoEtiquetado("Asignado", fechaHora(a.asignadoAt))
        } else if (flujo.asignacionConsultada) {
            FilaChips { EstadoChip("Sin proveedor asignado", Tono.AVISO, FhIcons.SinAsignar) }
        }
        // FH-006 / P6-C3: asignar o reasignar (gestion). En obligaciones del inquilino el backend lo impide salvo el
        // proveedor aceptado (403/409 con su mensaje).
        val activos = flujo.proveedores.filter { it.activo && it.id != a?.proveedorId }
        if (activos.isNotEmpty()) {
            Text(if (a == null) "Asignar proveedor" else "Reasignar a otro proveedor", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp))
            activos.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = proveedorElegido?.id == p.id, onClick = { proveedorElegido = p })
                    Text("${p.razonSocial}${p.cif?.let { " · $it" }.orEmpty()}")
                }
            }
            Button(onClick = { confirmarAsignacion = true }, enabled = proveedorElegido != null && !flujo.loading) {
                Text(if (a == null) "Asignar" else "Reasignar")
            }
        }
        Text("El cumplimiento se declara aprobando una intervención presentada; no hay finalización manual.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        SectionTitle("Intervenciones")
        ListaIntervenciones(flujo.intervencionesMantenimiento, onIntervencion)
    }

    proveedorElegido?.takeIf { confirmarAsignacion }?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmarAsignacion = false },
            title = { Text("Asignar proveedor") },
            text = { Text("Se asignará ${p.razonSocial}. Si había otra empresa asignada, perderá el acceso a este trabajo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarAsignacion = false
                    flujo.asignarProveedor(mantenimientoId, p.id)
                    proveedorElegido = null
                }) { Text("Asignar") }
            },
            dismissButton = { TextButton(onClick = { confirmarAsignacion = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun BandejaValidacionScreen(flujo: FlujoViewModel, onIntervencion: (Long) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { flujo.limpiarMensajes(); flujo.cargarPendientes() }
    ScreenScaffold(title = "Pendientes de validación", subtitle = "Explotación", onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        if (!flujo.loading && flujo.pendientes.isEmpty() && flujo.error == null) EstadoVacio("No hay intervenciones pendientes.", FhIcons.Hecho)
        flujo.pendientes.forEach { p ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs).clickable { onIntervencion(p.id) }
            ) {
                Column(modifier = Modifier.padding(FhEspacio.m)) {
                    Text(p.tipoMantenimiento, style = MaterialTheme.typography.titleSmall)
                    Text(listOfNotNull(p.centro, p.local ?: "Zona común", p.proveedor).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilaChips {
                        EstadoChip(etiquetaResultado(p.resultado), if (p.resultado == "CONFORME") Tono.EXITO else Tono.AVISO)
                        if (p.ejecucionAnticipada) EstadoChip("Anticipada", Tono.NEUTRO)
                    }
                    Text("Ejecución ${p.fechaEjecucion?.let { fechaCorta(it) } ?: "—"} · envío ${p.numeroEnvio} · responsable ${p.responsable}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)
            ) {
                Column(modifier = Modifier.padding(FhEspacio.m)) {
                    Text(p.razonSocial, style = MaterialTheme.typography.titleSmall)
                    p.cif?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    FilaChips {
                        EstadoChip(if (p.activo) "Activo" else "De baja", if (p.activo) Tono.EXITO else Tono.NEUTRO)
                        EstadoChip("Técnicos: ${p.tecnicos.orEmpty().count { it.activo }} activos", Tono.NEUTRO)
                    }
                }
            }
        }
    }
}
