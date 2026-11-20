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

// FH-006 / P6-C3: area del INQUILINO (obligaciones de sus ocupaciones vigentes) y revision de proveedores por gestion.
// Las obligaciones NHOOD se muestran solo para consulta: el backend no devuelve acciones para ellas.

internal fun etiquetaDeclaracion(e: String) = when (e) {
    "PROPUESTO" -> "Pendiente de aceptación"
    "ACEPTADO" -> "Aceptado"
    "RECHAZADO" -> "Rechazado"
    else -> e
}

@Composable
fun ObligacionesInquilinoScreen(
    flujo: FlujoViewModel,
    onObligacion: (ObligacionInquilinoDto) -> Unit,
    onSolicitudes: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { flujo.limpiarMensajes(); flujo.cargarObligacionesInquilino() }
    var declarando by remember { mutableStateOf<ObligacionInquilinoDto?>(null) }
    var solicitando by remember { mutableStateOf<ObligacionInquilinoDto?>(null) }

    ScreenScaffold(title = "Mis obligaciones", subtitle = "Inquilino", onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        TextButton(onClick = onSolicitudes) { Text("Mis solicitudes de proveedor") }
        if (!flujo.loading && flujo.obligacionesInquilino.isEmpty() && flujo.error == null) {
            Text("No tienes obligaciones en locales con ocupación vigente.")
        }
        flujo.obligacionesInquilino.forEach { o ->
            val acciones = o.accionesPermitidas.orEmpty()
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onObligacion(o) }) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(o.tipoMantenimiento, fontWeight = FontWeight.Bold)
                    Text("${o.local}${o.instalacion?.let { " · $it" }.orEmpty()}")
                    Text("Próxima fecha: ${o.proximaFecha}")
                    Text(if (o.responsable == "INQUILINO") "Responsable: inquilino (gestionas tú el proveedor)" else "Responsable: NHOOD (solo consulta)",
                        style = MaterialTheme.typography.bodySmall)
                    o.proveedorDeclarado?.let { d ->
                        Text("Proveedor declarado: ${d.proveedor} · ${etiquetaDeclaracion(d.estado)}")
                        d.motivoRevision?.takeIf { d.estado == "RECHAZADO" }?.let { Text("Motivo: $it", color = MaterialTheme.colorScheme.error) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ACCION_DECLARAR_PROVEEDOR in acciones) TextButton(onClick = { flujo.limpiarMensajes(); declarando = o }) { Text("Declarar proveedor") }
                        if (ACCION_SOLICITAR_PROVEEDOR in acciones) TextButton(onClick = { flujo.limpiarMensajes(); solicitando = o }) { Text("Solicitar uno nuevo") }
                    }
                }
            }
        }
    }

    declarando?.let { o -> DialogoDeclarar(flujo, o) { declarando = null } }
    solicitando?.let { o -> DialogoSolicitar(flujo, o) { solicitando = null } }
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
    var aDescargar by remember { mutableStateOf<CertificadoInquilinoDto?>(null) }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val c = aDescargar
        aDescargar = null
        if (uri != null && c != null) flujo.descargarDocumento(c.id, uri)
    }

    ScreenScaffold(title = "Mantenimientos", subtitle = titulo, onAction = onBack, scrollable = true) {
        MensajesFlujo(flujo)
        if (!flujo.loading && flujo.mantenimientosInquilino.isEmpty() && flujo.error == null) Text("Sin mantenimientos en tu ocupación.")
        flujo.mantenimientosInquilino.forEach { m ->
            val acciones = m.accionesPermitidas.orEmpty()
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${m.fechaProgramada} · ${m.estado}", fontWeight = FontWeight.Bold)
                    m.descripcion?.let { Text(it) }
                    m.fechaPrevistaInquilino?.let { Text("Fecha prevista: $it") }
                    Text("Proveedor asignado: ${m.proveedorAsignado ?: "sin asignar"}")
                    val c = m.cumplimiento
                    if (c != null) {
                        Text("Cumplimiento aprobado: ${c.fechaEjecucion.orEmpty()} · ${etiquetaResultado(c.resultado)}", color = MaterialTheme.colorScheme.primary)
                        if (c.certificados.isNullOrEmpty()) Text("Sin certificados compartidos.", style = MaterialTheme.typography.bodySmall)
                        // Solo llegan certificados APROBADOS y COMPARTIDOS de tu ocupacion; la descarga vuelve a comprobarlo.
                        c.certificados.orEmpty().forEach { cert ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cert.nombre, modifier = Modifier.weight(1f))
                                TextButton(onClick = { aDescargar = cert; guardar.launch(cert.nombre) }, enabled = !flujo.loading) { Text("Descargar") }
                            }
                        }
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
        if (!flujo.loading && flujo.solicitudesInquilino.isEmpty() && flujo.error == null) Text("No has enviado solicitudes.")
        flujo.solicitudesInquilino.forEach { s -> TarjetaSolicitud(s) }
    }
}

@Composable
private fun TarjetaSolicitud(s: SolicitudProveedorDto, contenido: @Composable ColumnScope.() -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${s.razonSocial} · ${s.estado}", fontWeight = FontWeight.Bold)
            Text(listOfNotNull(s.cif, s.inquilino, fechaHora(s.createdAt)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
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
        if (!flujo.loading && flujo.declaraciones.isEmpty() && flujo.error == null) Text("No hay declaraciones pendientes.")
        flujo.declaraciones.forEach { d ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${d.proveedor}${d.cif?.let { " · $it" }.orEmpty()}", fontWeight = FontWeight.Bold)
                    Text("${d.tipoMantenimiento} · ${d.local} · ${d.inquilino}")
                    Text("Declarado: ${fechaHora(d.declaradoAt)}", style = MaterialTheme.typography.bodySmall)
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
        if (!flujo.loading && flujo.solicitudesGestion.isEmpty() && flujo.error == null) Text("No hay solicitudes.")
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
        Text("Tu usuario no tiene un rol con acceso a la aplicación. Contacta con el administrador.")
    }
}
