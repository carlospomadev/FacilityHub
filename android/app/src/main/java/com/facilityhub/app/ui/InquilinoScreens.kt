package com.facilityhub.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

// FH-006 / P6-C3: area del INQUILINO (obligaciones de sus ocupaciones vigentes) y revision de proveedores por gestion.
// Las obligaciones NHOOD se muestran solo para consulta: el backend no devuelve acciones para ellas.

internal fun etiquetaDeclaracion(e: String) = when (e) {
    "PROPUESTO" -> "Pendiente de aceptación"
    "ACEPTADO" -> "Aceptado"
    "RECHAZADO" -> "Rechazado"
    else -> e
}

// FH-009 / R6: «Mi local» del INQUILINO con pestanas «Obligaciones» e historial del local (linea de tiempo), sin pasar
// por «Centros». El contenido lo decide el backend (obligaciones de sus ocupaciones vigentes; historial solo con
// cumplimientos aprobados y certificados compartidos). Las obligaciones NHOOD son solo de consulta.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObligacionesInquilinoScreen(
    flujo: FlujoViewModel,
    historial: HistorialViewModel,
    onObligacion: (ObligacionInquilinoDto) -> Unit,
    onBack: () -> Unit,
    onSesionCaducada: () -> Unit,
    // FH-009 / R3: las solicitudes son una pestana propia; aqui queda el acceso a la ficha del centro y del local.
    onCentro: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) { flujo.limpiarMensajes(); flujo.cargarObligacionesInquilino() }
    LaunchedEffect(historial.sesionCaducada) { if (historial.sesionCaducada) onSesionCaducada() }
    var declarando by remember { mutableStateOf<ObligacionInquilinoDto?>(null) }
    var solicitando by remember { mutableStateOf<ObligacionInquilinoDto?>(null) }
    val descargar = rememberDescargaDocumento(flujo)

    // Locales con ocupacion vigente (de las obligaciones que devuelve el backend).
    val locales = flujo.obligacionesInquilino.distinctBy { it.localId }.map { it.localId to it.local }
    var pestana by rememberSaveable { mutableStateOf(0) }
    var localElegido by rememberSaveable { mutableStateOf<Long?>(null) }
    val local = locales.firstOrNull { it.first == localElegido } ?: locales.firstOrNull()
    // El historial se pide al abrir su pestana (y al volver a ella); el backend vuelve a comprobar el acceso.
    LaunchedEffect(pestana, local?.first) { if (pestana == 1 && local != null) historial.cargar(local.first) }

    ScreenScaffold(title = "Mis obligaciones", subtitle = local?.second?.let { "Mi local · $it" } ?: "Mi local · Inquilino", onAction = onBack) {
        val n = flujo.obligacionesInquilino.size
        PestanasInternas(
            titulos = listOf("Obligaciones · $n", if (locales.size == 1) "Historial del local ${locales[0].second}" else "Historial"),
            seleccionada = pestana,
            onSeleccionar = { pestana = it }
        )
        Spacer(Modifier.height(FhEspacio.s))
        if (pestana == 0) {
            MensajesFlujo(flujo)
            if (!flujo.loading && flujo.obligacionesInquilino.isEmpty() && flujo.error == null) {
                EstadoVacio("No tienes obligaciones en locales con ocupación vigente.", FhIcons.Edificio)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(FhEspacio.s), contentPadding = PaddingValues(bottom = FhEspacio.l)) {
                if (onCentro != null) item(key = "centro") {
                    TextButton(onClick = onCentro, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(FhIcons.Centros, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(FhEspacio.s))
                        Text("Mi centro y local")
                    }
                }
                items(flujo.obligacionesInquilino, key = { it.id }) { o ->
                    TarjetaObligacion(o, onAbrir = { onObligacion(o) },
                        onDeclarar = { flujo.limpiarMensajes(); declarando = o }, onSolicitar = { flujo.limpiarMensajes(); solicitando = o })
                }
            }
        } else {
            if (locales.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
                    locales.forEach { (id, nombre) ->
                        FilterChip(selected = local?.first == id, onClick = { localElegido = id }, label = { Text(nombre) })
                    }
                }
            }
            if (local == null) {
                if (!flujo.loading) EstadoVacio("No tienes locales con ocupación vigente.", FhIcons.Edificio)
            } else {
                EstadoHistorial(historial, flujo, local.first)
                LazyColumn(contentPadding = PaddingValues(bottom = FhEspacio.l)) {
                    // Solo se muestra el historial del local elegido (el ViewModel podria conservar otro durante la carga).
                    if (historial.localId == local.first) {
                        lineaTemporalHistorial(historial, enabled = !(historial.loading || flujo.loading), onDescargar = descargar)
                    }
                }
            }
        }
    }

    declarando?.let { o -> DialogoDeclarar(flujo, o) { declarando = null } }
    solicitando?.let { o -> DialogoSolicitar(flujo, o) { solicitando = null } }
}

/** Dias hasta la proxima fecha (informativo: no cambia el estado de cumplimiento, que decide el backend). */
private fun diasHasta(fecha: String): Long? = try {
    ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of("Europe/Madrid")), LocalDate.parse(fecha))
} catch (_: Exception) { null }

internal fun textoPeriodicidad(meses: Int?): String? = when (meses) {
    null -> null
    1 -> "Mensual"
    3 -> "Trimestral (cada 3 meses)"
    6 -> "Semestral (cada 6 meses)"
    12 -> "Anual (cada 12 meses)"
    else -> "Cada $meses meses"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaObligacion(o: ObligacionInquilinoDto, onAbrir: () -> Unit, onDeclarar: () -> Unit, onSolicitar: () -> Unit) {
    val acciones = o.accionesPermitidas.orEmpty()
    val dias = diasHasta(o.proximaFecha)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Ver mantenimientos", role = Role.Button, onClick = onAbrir)
    ) {
        Column(Modifier.padding(FhEspacio.m), verticalArrangement = Arrangement.spacedBy(FhEspacio.xs)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(o.tipoMantenimiento, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(FhIcons.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(listOfNotNull(o.local, o.instalacion).joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilaChips {
                // Vencimiento relativo informativo (texto e icono, no solo color).
                if (dias != null) EstadoChip(textoDias(dias), when { dias < 0 -> Tono.ERROR; dias <= 30 -> Tono.AVISO; else -> Tono.NEUTRO },
                    if (dias < 0) FhIcons.Vencido else FhIcons.Calendario)
                EstadoChip(if (o.responsable == "INQUILINO") "Responsable: inquilino (gestionas tú el proveedor)" else "Responsable: NHOOD (solo consulta)",
                    if (o.responsable == "INQUILINO") Tono.PRIMARIO else Tono.NEUTRO)
                o.proveedorDeclarado?.let { d ->
                    EstadoChip("Proveedor declarado: ${d.proveedor} · ${etiquetaDeclaracion(d.estado)}",
                        when (d.estado) { "ACEPTADO" -> Tono.EXITO; "RECHAZADO" -> Tono.ERROR; else -> Tono.AVISO })
                }
            }
            DatoEtiquetado("Próxima fecha", fechaCorta(o.proximaFecha))
            textoPeriodicidad(o.periodicidadMeses)?.let { DatoEtiquetado("Periodicidad", it) }
            o.proveedorDeclarado?.let { d ->
                d.motivoRevision?.takeIf { d.estado == "RECHAZADO" }?.let { Text("Motivo: $it", color = MaterialTheme.colorScheme.error) }
            }
            if (ACCION_DECLARAR_PROVEEDOR in acciones || ACCION_SOLICITAR_PROVEEDOR in acciones) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ACCION_DECLARAR_PROVEEDOR in acciones) TextButton(onClick = onDeclarar) { Text("Declarar proveedor") }
                    if (ACCION_SOLICITAR_PROVEEDOR in acciones) TextButton(onClick = onSolicitar) { Text("Solicitar uno nuevo") }
                }
            }
        }
    }
}

// Busqueda exacta por CIF (sin listar el catalogo de proveedores) y confirmacion de la declaracion.
@Composable
private fun DialogoDeclarar(flujo: FlujoViewModel, o: ObligacionInquilinoDto, onCerrar: () -> Unit) {
    var cif by remember { mutableStateOf("") }
    val encontrado = flujo.proveedorEncontrado
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Declarar proveedor") },
        text = {
            Column {
                Text("Introduce el CIF de una empresa ya registrada. Explotación deberá aceptarla.")
                OutlinedTextField(cif, { cif = it }, label = { Text("CIF") }, singleLine = true)
                TextButton(onClick = { flujo.buscarProveedor(cif) }, enabled = cif.isNotBlank() && !flujo.loading) { Text("Buscar") }
                encontrado?.let { Text("Encontrado: ${it.razonSocial}", fontWeight = FontWeight.Bold) }
                flujo.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { encontrado?.let { p -> flujo.declararProveedor(o.id, p.id, onCerrar) } },
                enabled = encontrado != null && !flujo.loading) { Text("Declarar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoSolicitar(flujo: FlujoViewModel, o: ObligacionInquilinoDto, onCerrar: () -> Unit) {
    var razon by remember { mutableStateOf("") }
    var cif by remember { mutableStateOf("") }
    var contacto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Solicitar proveedor nuevo") },
        text = {
            Column {
                Text("Un administrador dará de alta la empresa; después podrás declararla.")
                OutlinedTextField(razon, { razon = it }, label = { Text("Razón social") }, singleLine = true)
                OutlinedTextField(cif, { cif = it }, label = { Text("CIF (opcional)") }, singleLine = true)
                OutlinedTextField(contacto, { contacto = it }, label = { Text("Contacto (opcional)") })
                flujo.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                flujo.solicitarProveedor(SolicitudProveedorRequest(razon.trim(), cif.ifBlank { null }, contacto.ifBlank { null }, null, o.id), onCerrar)
            }, enabled = razon.isNotBlank() && !flujo.loading) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

@Composable
fun MantenimientosInquilinoScreen(
    flujo: FlujoViewModel,
    obligacionId: Long,
    titulo: String,
    onBack: () -> Unit
) {
    LaunchedEffect(obligacionId) { flujo.limpiarMensajes(); flujo.cargarMantenimientosInquilino(obligacionId) }
    var fechaDe by remember { mutableStateOf<MantenimientoInquilinoDetalleDto?>(null) }
    var asignarDe by remember { mutableStateOf<MantenimientoInquilinoDetalleDto?>(null) }
    val descargar = rememberDescargaDocumento(flujo)

    ScreenScaffold(title = "Mantenimientos", subtitle = titulo, onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        if (!flujo.loading && flujo.mantenimientosInquilino.isEmpty() && flujo.error == null) EstadoVacio("Sin mantenimientos en tu ocupación.")
        flujo.mantenimientosInquilino.forEach { m ->
            val acciones = m.accionesPermitidas.orEmpty()
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)
            ) {
                Column(modifier = Modifier.padding(FhEspacio.m)) {
                    Text(fechaCorta(m.fechaProgramada), style = MaterialTheme.typography.titleSmall)
                    FilaChips {
                        EstadoChip(etiquetaEstadoInquilino(m.estado, m.historicoP5), if (m.historicoP5) Tono.HISTORICO else tonoEstado(m.estado))
                    }
                    // FH-006 / P6-D3-6: un registro P5 conserva su estado real, pero no acredita cumplimiento.
                    if (m.historicoP5) {
                        Text("Histórico P5 — sin validación: no acredita cumplimiento.", color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    m.descripcion?.let { Text(it, modifier = Modifier.padding(top = FhEspacio.xs)) }
                    m.fechaPrevistaInquilino?.let { Text("Fecha prevista: ${fechaCorta(it)}", style = MaterialTheme.typography.bodyMedium) }
                    Text("Proveedor asignado: ${m.proveedorAsignado ?: "sin asignar"}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    m.cumplimiento?.let { c ->
                        CumplimientoCertificados(c, enabled = !flujo.loading) { cert -> descargar(cert.id, cert.nombre) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ACCION_FIJAR_FECHA_PREVISTA in acciones) TextButton(onClick = { flujo.limpiarMensajes(); fechaDe = m }) { Text("Fecha prevista") }
                        if (ACCION_ASIGNAR_PROVEEDOR in acciones) TextButton(onClick = { flujo.limpiarMensajes(); asignarDe = m }) { Text("Asignar proveedor aceptado") }
                    }
                }
            }
        }
    }

    fechaDe?.let { m ->
        var fecha by remember(m.id) { mutableStateOf(m.fechaPrevistaInquilino ?: m.fechaProgramada) }
        AlertDialog(
            onDismissRequest = { fechaDe = null },
            title = { Text("Fecha prevista") },
            text = { OutlinedTextField(fecha, { fecha = it }, label = { Text("AAAA-MM-DD") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { fechaDe = null; flujo.fijarFechaPrevista(m, fecha) }, enabled = !flujo.loading) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { fechaDe = null }) { Text("Cancelar") } }
        )
    }
    asignarDe?.let { m ->
        AlertDialog(
            onDismissRequest = { asignarDe = null },
            title = { Text("Asignar proveedor") },
            text = { Text("Se asignará el proveedor aceptado por Explotación para tu ocupación.") },
            confirmButton = { TextButton(onClick = { asignarDe = null; flujo.asignarProveedorAceptado(m) }, enabled = !flujo.loading) { Text("Asignar") } },
            dismissButton = { TextButton(onClick = { asignarDe = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun SolicitudesInquilinoScreen(flujo: FlujoViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { flujo.limpiarMensajes(); flujo.cargarSolicitudesInquilino() }
    ScreenScaffold(title = "Mis solicitudes", subtitle = "Proveedores nuevos", onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        if (!flujo.loading && flujo.solicitudesInquilino.isEmpty() && flujo.error == null) EstadoVacio("No has enviado solicitudes.", FhIcons.Solicitud)
        flujo.solicitudesInquilino.forEach { s -> TarjetaSolicitud(s) }
    }
}

@Composable
private fun TarjetaSolicitud(s: SolicitudProveedorDto, contenido: @Composable ColumnScope.() -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)
    ) {
        Column(modifier = Modifier.padding(FhEspacio.m)) {
            Text(s.razonSocial, style = MaterialTheme.typography.titleSmall)
            FilaChips {
                EstadoChip(etiquetaEstadoSolicitud(s.estado), when (s.estado) { "ATENDIDA" -> Tono.EXITO; "RECHAZADA" -> Tono.ERROR; else -> Tono.AVISO })
            }
            Text(listOfNotNull(s.cif, s.inquilino, fechaHora(s.createdAt)).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.proveedor?.let { Text("Empresa dada de alta: $it") }
            s.respuesta?.let { Text("Respuesta: $it") }
            contenido()
        }
    }
}

// ---------- Gestion ----------

@Composable
fun DeclaracionesScreen(flujo: FlujoViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { flujo.limpiarMensajes(); flujo.cargarDeclaraciones() }
    var revisando by remember { mutableStateOf<Pair<DeclaracionPendienteDto, Boolean>?>(null) }
    ScreenScaffold(title = "Proveedores declarados", subtitle = "Pendientes de revisión", onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        if (!flujo.loading && flujo.declaraciones.isEmpty() && flujo.error == null) EstadoVacio("No hay declaraciones pendientes.", FhIcons.Hecho)
        flujo.declaraciones.forEach { d ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)
            ) {
                Column(modifier = Modifier.padding(FhEspacio.m)) {
                    Text("${d.proveedor}${d.cif?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                    Text("${d.tipoMantenimiento} · ${d.local} · ${d.inquilino}", style = MaterialTheme.typography.bodyMedium)
                    Text("Declarado: ${fechaHora(d.declaradoAt)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { revisando = d to true }, enabled = !flujo.loading) { Text("Aceptar") }
                        OutlinedButton(onClick = { revisando = d to false }, enabled = !flujo.loading) { Text("Rechazar") }
                    }
                }
            }
        }
    }
    revisando?.let { (d, aceptar) ->
        var motivo by remember(d.id, aceptar) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { revisando = null },
            title = { Text(if (aceptar) "Aceptar proveedor" else "Rechazar proveedor") },
            text = { OutlinedTextField(motivo, { motivo = it }, label = { Text(if (aceptar) "Comentario (opcional)" else "Motivo (obligatorio)") }) },
            confirmButton = {
                TextButton(onClick = { revisando = null; flujo.revisarDeclaracion(d, aceptar, motivo) },
                    enabled = (aceptar || motivo.isNotBlank()) && !flujo.loading) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { revisando = null }) { Text("Cancelar") } }
        )
    }
}

// Solicitudes de proveedor nuevo: consulta gestion; resolver solo ADMIN (vinculando una empresa ya dada de alta).
@Composable
fun SolicitudesGestionScreen(flujo: FlujoViewModel, esAdmin: Boolean, onBack: () -> Unit) {
    LaunchedEffect(Unit) { flujo.limpiarMensajes(); flujo.cargarSolicitudesGestion(); if (esAdmin) flujo.cargarProveedores() }
    var resolviendo by remember { mutableStateOf<Pair<SolicitudProveedorDto, Boolean>?>(null) }
    ScreenScaffold(title = "Solicitudes de proveedor", subtitle = if (esAdmin) "Administración" else "Consulta", onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        if (!flujo.loading && flujo.solicitudesGestion.isEmpty() && flujo.error == null) EstadoVacio("No hay solicitudes.", FhIcons.Solicitud)
        flujo.solicitudesGestion.forEach { s ->
            TarjetaSolicitud(s) {
                if (esAdmin && s.estado == "PENDIENTE") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { resolviendo = s to true }, enabled = !flujo.loading) { Text("Atender") }
                    OutlinedButton(onClick = { resolviendo = s to false }, enabled = !flujo.loading) { Text("Rechazar") }
                }
            }
        }
    }
    resolviendo?.let { (s, atender) ->
        var respuesta by remember(s.id, atender) { mutableStateOf("") }
        var proveedorId by remember(s.id) { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { resolviendo = null },
            title = { Text(if (atender) "Atender solicitud" else "Rechazar solicitud") },
            text = {
                Column {
                    if (atender) {
                        Text("Da de alta la empresa por el circuito de proveedores y selecciónala:")
                        flujo.proveedores.filter { it.activo }.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = proveedorId == p.id, onClick = { proveedorId = p.id })
                                Text("${p.razonSocial}${p.cif?.let { " · $it" }.orEmpty()}")
                            }
                        }
                    }
                    OutlinedTextField(respuesta, { respuesta = it }, label = { Text(if (atender) "Respuesta (opcional)" else "Respuesta (obligatoria)") })
                }
            },
            confirmButton = {
                TextButton(onClick = { resolviendo = null; flujo.resolverSolicitud(s, atender, proveedorId, respuesta) },
                    enabled = !flujo.loading && (if (atender) proveedorId != null else respuesta.isNotBlank())) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { resolviendo = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun SinPermisosScreen(onLogout: () -> Unit) {
    ScreenScaffold(title = "Sin acceso", subtitle = "Rol no reconocido", onAction = onLogout, actionLabel = "Salir") {
        Mensaje("Tu usuario no tiene un rol con acceso a la aplicación. Contacta con el administrador.", Tono.AVISO)
    }
}

// FH-006 / P6-D3-4: componentes comunes de certificados (Mantenimientos del inquilino e historial del local).

// Guarda un documento elegido por el usuario. La autorizacion la vuelve a comprobar el backend en la descarga
// (GET /api/documentos/{id}/archivo); un 403/404 llega como error de FlujoViewModel.
@Composable
internal fun rememberDescargaDocumento(flujo: FlujoViewModel): (Long, String) -> Unit {
    var pendiente by remember { mutableStateOf<Long?>(null) }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val id = pendiente
        pendiente = null
        if (uri != null && id != null) flujo.descargarDocumento(id, uri)
    }
    return { id, nombre -> pendiente = id; guardar.launch(nombre) }
}

// Cumplimiento aprobado y sus certificados. Solo llegan certificados VALIDO + COMPARTIDO de la ocupacion del inquilino.
@Composable
internal fun CumplimientoCertificados(c: CumplimientoInquilinoDto, enabled: Boolean, onDescargar: (CertificadoInquilinoDto) -> Unit) {
    FilaChips(Modifier.padding(top = FhEspacio.xs)) {
        EstadoChip("Cumplimiento aprobado", Tono.EXITO, FhIcons.Verificado)
        EstadoChip(etiquetaResultado(c.resultado), Tono.NEUTRO)
    }
    c.fechaEjecucion?.let {
        Text("Ejecución ${fechaCorta(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (c.certificados.isNullOrEmpty()) Text("Sin certificados compartidos.", style = MaterialTheme.typography.bodySmall)
    c.certificados.orEmpty().forEach { cert ->
        FilaDocumento(
            nombre = cert.nombre,
            detalle = "Certificado compartido",
            accion = { BotonDescargar(cert.nombre, enabled) { onDescargar(cert) } }
        )
    }
}

// FH-006 / P6-D3-6: estado legible. FINALIZADO en P6 = ejecutado y enviado (pendiente de validar si no hay cumplimiento);
// en un registro P5 es solo el estado historico, sin validacion.
internal fun etiquetaEstadoInquilino(estado: String, historicoP5: Boolean) = when {
    historicoP5 && estado == "FINALIZADO" -> "Finalizado (registro P5)"
    else -> etiquetaEstado(estado) ?: estado
}
