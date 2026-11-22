package com.facilityhub.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

    // Validacion. El comentario y la seleccion de certificados solo se reinician cuando cambia la version de la
    // intervencion (operacion correcta): tras un error de servidor o una cancelacion se conservan.
    var comentario by remember(i?.id, i?.version) { mutableStateOf("") }
    var compartir by remember(i?.id, i?.version) { mutableStateOf(setOf<Long>()) }
    var confirmar by remember { mutableStateOf<String?>(null) }
    // Se ha confirmado una validacion en esta pantalla: su resultado (error o exito) se muestra en la barra fija, junto a
    // donde el usuario ha pulsado, aunque la intervencion ya no admita mas acciones.
    var validacionLanzada by remember(intervencionId) { mutableStateOf(false) }

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

    // Acciones de validacion: solo las que el backend incluye en accionesPermitidas (no se crean condiciones nuevas).
    val accionesValidacion = if (acciones.contains(ACCION_EDITAR_INTERVENCION)) emptyList()
        else listOf(ACCION_APROBAR, ACCION_SOLICITAR_CORRECCION, ACCION_CERRAR_SIN_CUMPLIMIENTO).filter { it in acciones }
    val certificados = i?.documentos.orEmpty().filter { it.categoria == "CERTIFICADO" }
    val conBarra = i != null && (accionesValidacion.isNotEmpty() ||
        (validacionLanzada && (flujo.error != null || flujo.aviso != null || flujo.loading)))

    ScreenScaffold(
        title = "Intervención",
        subtitle = i?.let { etiquetaValidacion(it.estadoValidacion) } ?: "",
        onAction = onBack,
        scrollable = true,
        // FH-009 / R5: acciones fijas encima de la barra de gestos (siempre visibles al desplazarse).
        barraInferior = if (conBarra) {
            {
                // Mensajes de la operacion (carga, error, exito) junto a las acciones, siempre visibles.
                EstadoCargando(flujo.loading)
                flujo.error?.let { EstadoError(it) }
                flujo.aviso?.let { Mensaje(it, Tono.EXITO) }
                if (accionesValidacion.isNotEmpty()) BarraValidacion(
                    acciones = accionesValidacion,
                    hayComentario = comentario.isNotBlank(),
                    hayCertificados = certificados.isNotEmpty(),
                    ocupado = flujo.loading,
                    onAccion = { confirmar = it }
                )
            }
        } else null
    ) {
        // Con barra fija, los mensajes van en ella (no arriba, fuera de la vista de quien ha pulsado una accion).
        if (!conBarra) MensajesFlujo(flujo)
        if (i == null) return@ScreenScaffold

        FilaChips(Modifier.padding(bottom = FhEspacio.s)) {
            EstadoChip(etiquetaValidacion(i.estadoValidacion), tonoValidacion(i.estadoValidacion), iconoValidacion(i.estadoValidacion))
            if (i.resultado != null) EstadoChip(etiquetaResultado(i.resultado),
                if (i.resultado == "CONFORME") Tono.EXITO else Tono.AVISO, if (i.resultado == "CONFORME") FhIcons.Hecho else null)
            if (i.ejecucionAnticipada) EstadoChip("Ejecución anticipada", Tono.NEUTRO)
        }
        // Datos como tabla clave/valor (sin parrafos con varios datos).
        SectionTitle("Datos")
        DatoEtiquetado("Estado", etiquetaValidacion(i.estadoValidacion))
        DatoEtiquetado("Proveedor", i.proveedor ?: "—")
        DatoEtiquetado("Técnico", i.tecnico ?: "—")

        if (acciones.contains(ACCION_EDITAR_INTERVENCION)) {
            DatoEtiquetado("Envío", "nº ${i.numeroEnvio}")
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
            DatoEtiquetado("Resultado", etiquetaResultado(i.resultado))
            DatoEtiquetado("Ejecución", i.fechaEjecucion?.let { fechaCorta(it) } ?: "—")
            DatoEtiquetado("Enviada", i.enviadaAt?.let { fechaHora(it) } ?: "Sin enviar")
            if (i.numeroEnvio > 0) DatoEtiquetado("Nº de envío", i.numeroEnvio.toString())
            i.enviadaPor?.let { DatoEtiquetado("Enviada por", it) }
            i.actuaciones?.let { DatoEtiquetado("Actuaciones", it) }
            i.incidencias?.takeIf { it.isNotBlank() }?.let { DatoEtiquetado("Incidencias", it) }
            DatoEtiquetado("Registro", (if (i.origen == "REGISTRO_GESTION") "Registro de gestión" else "Proveedor") +
                i.registradaPor?.let { " · $it" }.orEmpty())
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Documentos")
        if (ACCION_APROBAR in accionesValidacion && certificados.isNotEmpty() && LocalDensity.current.fontScale >= 1.3f) {
            Text("Solo se comparten con el inquilino los certificados marcados.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            // La visibilidad se muestra tal cual: un documento INTERNO nunca se presenta como compartido. Solo los
            // certificados pueden marcarse para compartir, y solo si el backend permite aprobar.
            FilaDocumento(
                nombre = d.nombreOriginal,
                chips = {
                    EstadoChip(etiquetaCategoria(d.categoria), if (d.categoria == "CERTIFICADO") Tono.PRIMARIO else Tono.NEUTRO)
                    EstadoChip(etiquetaRevision(d.estadoRevision), tonoRevision(d.estadoRevision))
                    EstadoChip(etiquetaVisibilidad(d.visibilidad), if (d.visibilidad == "COMPARTIDO") Tono.EXITO else Tono.NEUTRO)
                },
                accion = { BotonDescargar(d.nombreOriginal, enabled = !flujo.loading) { aDescargar = d; guardar.launch(d.nombreOriginal) } },
                pie = if (ACCION_APROBAR in accionesValidacion && d.categoria == "CERTIFICADO") {
                    {
                        // Fila completa pulsable (casilla + texto), a todo el ancho de la tarjeta.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = d.id in compartir,
                                    enabled = !flujo.loading,
                                    role = Role.Checkbox,
                                    onValueChange = { marcado -> compartir = if (marcado) compartir + d.id else compartir - d.id }
                                )
                        ) {
                            Checkbox(checked = d.id in compartir, onCheckedChange = null, enabled = !flujo.loading)
                            Spacer(Modifier.width(FhEspacio.s))
                            Text("Compartir con el inquilino al aprobar", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else null
            )
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

        if (accionesValidacion.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Validación")
            OutlinedTextField(
                comentario, { comentario = it },
                label = { Text("Comentario") },
                supportingText = {
                    Text(if (ACCION_SOLICITAR_CORRECCION in accionesValidacion || ACCION_CERRAR_SIN_CUMPLIMIENTO in accionesValidacion)
                        "Obligatorio para pedir corrección o cerrar; opcional al aprobar." else "Opcional.")
                },
                enabled = !flujo.loading,
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
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
    // FH-009 / R5: confirmaciones. Cancelar no realiza ninguna peticion y conserva comentario y seleccion; confirmar lanza
    // una sola vez la operacion existente (la guarda de FlujoViewModel ignora toques repetidos).
    confirmar?.let { accion ->
        if (i != null) AlertDialog(
            onDismissRequest = { confirmar = null },
            title = {
                Text(when (accion) {
                    ACCION_APROBAR -> "¿Aprobar la intervención?"
                    ACCION_CERRAR_SIN_CUMPLIMIENTO -> "¿Cerrar sin cumplimiento?"
                    else -> "¿Pedir corrección?"
                })
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
                    when (accion) {
                        ACCION_APROBAR -> {
                            Text("Se declarará el cumplimiento del mantenimiento y se recalculará su próxima fecha.")
                            val marcados = certificados.filter { it.id in compartir }
                            if (marcados.isEmpty()) {
                                Text("No se compartirá ningún documento con el inquilino: todos quedarán internos.")
                            } else {
                                Text("Solo se compartirán con el inquilino estos certificados:")
                                marcados.forEach { Text("• ${it.nombreOriginal}", fontWeight = FontWeight.SemiBold) }
                                Text("El resto de documentos quedará interno.")
                            }
                        }
                        ACCION_CERRAR_SIN_CUMPLIMIENTO -> {
                            Text("El intento queda registrado sin cumplimiento; no cambia la próxima fecha y el mantenimiento sigue pendiente.")
                            Text("Motivo: «${comentario.trim()}»", fontWeight = FontWeight.SemiBold)
                        }
                        else -> {
                            Text("El proveedor podrá modificar y reenviar la intervención.")
                            Text("Motivo: «${comentario.trim()}»", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Si cancelas, no se envía nada" + if (accion == ACCION_APROBAR) "." else " y el comentario se conserva.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmar = null; validacionLanzada = true; flujo.validar(i, accion, comentario, compartir.toList()) }) {
                    Text("Confirmar")
                }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }
        )
    }
}

/**
 * FH-009 / R5: barra fija de validacion. Explica por que una accion esta desactivada; con fuente grande o tres acciones,
 * los botones se apilan a todo el ancho.
 */
@Composable
private fun ColumnScope.BarraValidacion(
    acciones: List<String>,
    hayComentario: Boolean,
    hayCertificados: Boolean,
    ocupado: Boolean,
    onAccion: (String) -> Unit
) {
    val requiereComentario = ACCION_SOLICITAR_CORRECCION in acciones || ACCION_CERRAR_SIN_CUMPLIMIENTO in acciones
    if (requiereComentario && !hayComentario) {
        Text("Para «Pedir corrección» escribe un comentario.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val fuenteGrande = LocalDensity.current.fontScale >= 1.3f
    // Con fuente grande esta nota va en la seccion «Documentos» para que la barra no ocupe media pantalla.
    if (ACCION_APROBAR in acciones && hayCertificados && !fuenteGrande) {
        Text("Solo se comparten con el inquilino los certificados marcados.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val apilar = fuenteGrande || acciones.size > 2
    val botones: @Composable (Modifier) -> Unit = { m ->
        if (ACCION_APROBAR in acciones) {
            Button(onClick = { onAccion(ACCION_APROBAR) }, enabled = !ocupado, modifier = m.heightIn(min = 48.dp)) { Text("Aprobar") }
        }
        if (ACCION_SOLICITAR_CORRECCION in acciones) {
            OutlinedButton(
                onClick = { onAccion(ACCION_SOLICITAR_CORRECCION) }, enabled = !ocupado && hayComentario,
                modifier = m.heightIn(min = 48.dp).semantics {
                    if (!hayComentario) stateDescription = "Desactivado: escribe un comentario"
                }
            ) { Text("Pedir corrección") }
        }
        if (ACCION_CERRAR_SIN_CUMPLIMIENTO in acciones) {
            OutlinedButton(
                onClick = { onAccion(ACCION_CERRAR_SIN_CUMPLIMIENTO) }, enabled = !ocupado && hayComentario,
                modifier = m.heightIn(min = 48.dp).semantics {
                    if (!hayComentario) stateDescription = "Desactivado: escribe un comentario"
                }
            ) { Text("Cerrar sin cumplimiento") }
        }
    }
    if (apilar) {
        Column(verticalArrangement = Arrangement.spacedBy(FhEspacio.xs), modifier = Modifier.fillMaxWidth()) { botones(Modifier.fillMaxWidth()) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(FhEspacio.s), modifier = Modifier.fillMaxWidth()) { botones(Modifier.weight(1f)) }
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
