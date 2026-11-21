package com.facilityhub.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.*

@Composable
fun LoginScreen(
    vm: FacilityViewModel,
    onLoggedIn: () -> Unit
) {
    var email by remember {
        mutableStateOf("explotacion@facilityhub.local")
    }

    var password by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FacilityHub",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Mantenimiento preventivo y cumplimiento documental"
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = {
                Text("Correo")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = {
                Text("Contraseña")
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = {
                vm.login(
                    email = email,
                    password = password,
                    onSuccess = onLoggedIn
                )
            },
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (vm.loading) {
                    "Accediendo..."
                } else {
                    "Iniciar sesión"
                }
            )
        }

        vm.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}


@Composable
fun CentrosScreen(
    vm: FacilityViewModel,
    onCentro: (CentroDto) -> Unit,
    onLogout: () -> Unit,
    onMenu: (String) -> Unit = {},
    accionAlertas: (@Composable () -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        if (vm.centros.isEmpty()) {
            vm.cargarCentros()
        }
    }

    ScreenScaffold(
        title = "Centros",
        subtitle = "${vm.userName} · ${vm.role}",
        onAction = onLogout,
        accionExtra = accionAlertas
    ) {
        // FH-006 / P6-C3: accesos por rol (la navegacion no sustituye a los controles del backend).
        if (vm.puedeGestionar) {
            // FH-006 / P6-D3-3: panel de Explotacion (ADMIN y EXPLOTACION).
            FilledTonalButton(onClick = { onMenu("panel") }, modifier = Modifier.fillMaxWidth()) { Text("Panel de Explotación") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { onMenu("validaciones") }) { Text("Validaciones") }
                OutlinedButton(onClick = { onMenu("proveedores") }) { Text("Proveedores") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onMenu("declaraciones") }) { Text("Declarados") }
                OutlinedButton(onClick = { onMenu("solicitudes-gestion") }) { Text("Solicitudes") }
            }
        } else if (vm.esInquilino) {
            FilledTonalButton(onClick = { onMenu("inquilino-obligaciones") }) { Text("Mis obligaciones y proveedores") }
        }

        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.centros) { centro ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCentro(centro)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = centro.nombre,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = listOfNotNull(
                                centro.codigo,
                                centro.ciudad
                            ).joinToString(" · ")
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun LocalesScreen(
    vm: FacilityViewModel,
    centroId: Long,
    centroNombre: String,
    onLocal: (LocalDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(centroId) {
        vm.cargarLocales(centroId)
    }

    ScreenScaffold(
        title = "Locales",
        subtitle = centroNombre,
        onAction = onBack
    ) {
        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.locales) { local ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLocal(local)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = local.nombre ?: local.codigo,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = listOfNotNull(
                                local.codigo,
                                local.planta,
                                local.ubicacion
                            ).joinToString(" · ")
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun LocalDetalleScreen(
    vm: FacilityViewModel,
    localId: Long,
    localNombre: String,
    onObligacion: (ObligacionDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(localId) {
        vm.cargarDetalleLocal(localId)
    }

    ScreenScaffold(
        title = localNombre,
        subtitle = "Detalle del local",
        onAction = onBack
    ) {
        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        vm.inquilino?.let { inquilino ->
            SectionTitle("Inquilino actual")

            Text(
                text = inquilino.nombreComercial ?: inquilino.razonSocial,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = inquilino.emailContacto.orEmpty()
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        SectionTitle("Instalaciones")

        vm.instalaciones.forEach { instalacion ->
            Text(
                text = "• ${instalacion.nombre} (${instalacion.estado})"
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        SectionTitle("Obligaciones de mantenimiento")

        vm.obligaciones.forEach { obligacion ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable {
                        onObligacion(obligacion)
                    }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = obligacion.tipoMantenimiento,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Próxima fecha: ${obligacion.proximaFecha}"
                    )

                    Text(
                        text = "Periodicidad: ${obligacion.periodicidadMeses} meses · ${obligacion.estado}"
                    )
                }
            }
        }
    }
}


@Composable
fun MantenimientosScreen(
    vm: FacilityViewModel,
    obligacion: ObligacionDto,
    onDocumentos: (MantenimientoDto) -> Unit,
    onIntervenciones: (MantenimientoDto) -> Unit = {},
    onBack: () -> Unit
) {
    var fecha by remember {
        mutableStateOf(obligacion.proximaFecha)
    }

    var descripcion by remember {
        mutableStateOf("")
    }

    // La proxima fecha se toma de las obligaciones recargadas tras finalizar.
    val proximaFecha =
        vm.obligaciones.firstOrNull { it.id == obligacion.id }?.proximaFecha
            ?: obligacion.proximaFecha

    var fechaPorDefecto by remember {
        mutableStateOf(obligacion.proximaFecha)
    }

    // El campo Fecha sigue a la proxima fecha solo si el usuario no lo ha editado.
    LaunchedEffect(proximaFecha) {
        if (fecha == fechaPorDefecto) {
            fecha = proximaFecha
        }
        fechaPorDefecto = proximaFecha
    }

    LaunchedEffect(obligacion.id) {
        vm.cargarMantenimientos(obligacion.id)
    }

    ScreenScaffold(
        title = "Mantenimientos",
        subtitle = obligacion.tipoMantenimiento,
        onAction = onBack,
        scrollable = true
    ) {
        Text(
            text = "Próxima fecha: $proximaFecha"
        )

        vm.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Crear ejecuciones solo para gestion (ADMIN/EXPLOTACION); el INQUILINO solo consulta.
        if (vm.puedeGestionar) {
            Spacer(
                modifier = Modifier.height(10.dp)
            )

            SectionTitle("Nueva ejecución")

            OutlinedTextField(
                value = fecha,
                onValueChange = {
                    fecha = it
                },
                label = {
                    Text("Fecha (AAAA-MM-DD)")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = descripcion,
                onValueChange = {
                    descripcion = it
                },
                label = {
                    Text("Descripción")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Button(
                onClick = {
                    vm.crearMantenimiento(
                        obligacionId = obligacion.id,
                        fecha = fecha,
                        descripcion = descripcion.ifBlank { null }
                    ) {
                        descripcion = ""
                    }
                },
                enabled = !vm.loading
            ) {
                Text("Crear mantenimiento")
            }
        }

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        SectionTitle("Histórico / programados")

        vm.mantenimientos.forEach { mantenimiento ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "${mantenimiento.fechaProgramada} · ${mantenimiento.estado}",
                        fontWeight = FontWeight.Bold
                    )

                    mantenimiento.descripcion?.let { descripcion ->
                        Text(
                            text = descripcion
                        )
                    }

                    mantenimiento.fechaRealizacion?.let { fechaRealizacion ->
                        Text(
                            text = "Realizado: $fechaRealizacion"
                        )
                    }

                    // Iniciar solo para gestion. FH-006 / P6-C3: "Finalizar" se sustituye por el circuito de
                    // intervenciones (registro, envio y validacion); el cumplimiento lo declara la aprobacion.
                    if (vm.puedeGestionar) {
                        if (siguienteEstado(mantenimiento.estado) == "EN_PROCESO") OutlinedButton(
                            onClick = {
                                vm.cambiarEstadoMantenimiento(
                                    mantenimiento = mantenimiento,
                                    nuevoEstado = "EN_PROCESO",
                                    localId = obligacion.localId
                                )
                            },
                            enabled = !vm.loading
                        ) {
                            Text("Iniciar")
                        }

                        TextButton(onClick = { onIntervenciones(mantenimiento) }) {
                            Text("Intervenciones y asignación")
                        }
                    }

                    TextButton(
                        onClick = {
                            onDocumentos(mantenimiento)
                        }
                    ) {
                        Text("Documentos")
                    }
                }
            }
        }
    }

}


@Composable
fun DocumentosScreen(
    vm: FacilityViewModel,
    mantenimientoId: Long,
    titulo: String,
    onBack: () -> Unit
) {
    // Evita abrir dos selectores con toques repetidos.
    var selectorAbierto by remember {
        mutableStateOf(false)
    }

    val selector = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectorAbierto = false

        if (uri != null) {
            vm.subirDocumento(mantenimientoId, uri)
        }
    }

    LaunchedEffect(mantenimientoId) {
        vm.cargarDocumentos(mantenimientoId)
    }

    ScreenScaffold(
        title = "Documentos",
        subtitle = titulo,
        onAction = onBack,
        scrollable = true
    ) {
        // Adjuntar solo para gestion (ADMIN/EXPLOTACION).
        if (vm.puedeGestionar) {
            Button(
                onClick = {
                    if (!selectorAbierto) {
                        selectorAbierto = true
                        selector.launch(TIPOS_DOCUMENTO)
                    }
                },
                enabled = !vm.loading && !selectorAbierto
            ) {
                Text("Adjuntar archivo")
            }

            Text(
                text = "PDF, JPEG o PNG · máximo 20 MB",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        vm.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        SectionTitle("Documentos adjuntos")

        if (vm.documentos.isEmpty() && !vm.loading && vm.error == null) {
            Text("Sin documentos")
        }

        vm.documentos.forEach { documento ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = documento.nombreOriginal,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Estado: ${documento.estadoRevision}"
                    )

                    documento.fechaSubida?.let { fechaSubida ->
                        Text(
                            text = "Subido: ${fechaSubida.take(16).replace('T', ' ')}"
                        )
                    }

                    documento.tipoDocumento?.let { tipo ->
                        Text(
                            text = "Tipo: $tipo"
                        )
                    }

                    documento.observaciones?.let { observaciones ->
                        Text(
                            text = observaciones
                        )
                    }
                }
            }
        }
    }
}


private const val TITULO_MIS_TRABAJOS = "Mis trabajos"

// FH-006 / P6-B: trabajos asignados a la empresa del PROVEEDOR. Solo lectura: no hay botones de escritura;
// las acciones futuras (P6-C) se mostraran unicamente si el backend las incluye en accionesPermitidas.
@Composable
fun TrabajosScreen(
    vm: FacilityViewModel,
    onTrabajo: (TrabajoProveedorDto) -> Unit,
    onLogout: () -> Unit,
    accionAlertas: (@Composable () -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        vm.cargarTrabajos()
    }

    ScreenScaffold(
        title = TITULO_MIS_TRABAJOS,
        subtitle = "${vm.userName} · ${vm.role}",
        onAction = onLogout,
        accionExtra = accionAlertas
    ) {
        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        vm.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!vm.loading && vm.trabajos.isEmpty() && vm.error == null) {
            Text(
                text = "No tienes trabajos asignados."
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.trabajos) { trabajo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onTrabajo(trabajo)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "${trabajo.fechaProgramada} · ${trabajo.estado}",
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = trabajo.tipoMantenimiento
                        )

                        Text(
                            text = listOfNotNull(
                                trabajo.centro,
                                trabajo.local ?: "Zona común"
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall
                        )

                        // FH-006 / P6-C3: estado de la ultima intervencion de su empresa.
                        Text(
                            text = "Intervención: ${etiquetaValidacion(trabajo.estadoValidacion)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun TrabajoDetalleScreen(
    vm: FacilityViewModel,
    mantenimientoId: Long,
    flujo: FlujoViewModel? = null,
    onIntervencion: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    LaunchedEffect(mantenimientoId) {
        vm.cargarTrabajo(mantenimientoId)
    }

    ScreenScaffold(
        title = "Trabajo",
        subtitle = vm.trabajo?.tipoMantenimiento ?: "",
        onAction = onBack,
        scrollable = true
    ) {
        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        vm.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        vm.trabajo?.let { trabajo ->
            SectionTitle("Datos del trabajo")

            Text(text = "Fecha programada: ${trabajo.fechaProgramada}")
            Text(text = "Estado: ${trabajo.estado}")
            trabajo.descripcion?.let { Text(text = "Descripción: $it") }

            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle("Ubicación")

            Text(text = "Centro: ${trabajo.centro}")
            Text(text = "Local: ${trabajo.local ?: "Zona común"}")
            trabajo.instalacion?.let { Text(text = "Instalación: $it") }

            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle("Asignación")

            Text(text = "Responsable contractual: ${trabajo.responsable}")
            trabajo.tecnicoAsignado?.let { Text(text = "Técnico asignado: $it") }
            trabajo.asignadoAt?.let { Text(text = "Asignado: ${it.take(16).replace('T', ' ')}") }

            Spacer(modifier = Modifier.height(12.dp))

            // FH-006 / P6-C3: intervenciones propias; registrar solo si el backend lo permite.
            if (flujo != null) {
                IntervencionesTrabajoSection(flujo, trabajo, onIntervencion)
            } else if (trabajo.accionesPermitidas.isNullOrEmpty()) {
                Text(
                    text = "Consulta: no hay acciones disponibles para este trabajo.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


@Composable
internal fun ScreenScaffold(
    title: String,
    subtitle: String,
    onAction: () -> Unit,
    scrollable: Boolean = false,
    actionLabel: String? = null,
    accionExtra: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // FH-006 / P6-C3: la barra no se superpone a la barra de estado (pantalla edge-to-edge).
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // FH-006 / P6-D1: acceso a «Mis notificaciones» en las pantallas raiz.
                    accionExtra?.invoke()

                    TextButton(
                        onClick = onAction
                    ) {
                        Text(
                            // Pantallas raiz por rol (Centros, Mis trabajos): "Salir"; el resto: "Volver".
                            actionLabel ?: if (title == "Centros" || title == TITULO_MIS_TRABAJOS) {
                                "Salir"
                            } else {
                                "Volver"
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .then(
                    if (scrollable) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                ),
            content = content
        )
    }
}


@Composable
internal fun SectionTitle(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}