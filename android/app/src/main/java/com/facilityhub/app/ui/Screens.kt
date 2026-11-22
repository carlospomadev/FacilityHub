package com.facilityhub.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.*
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

@Composable
fun LoginScreen(
    vm: FacilityViewModel,
    onLoggedIn: () -> Unit
) {
    // FH-009 / R2: el correo empieza vacio (sin cuentas precargadas). La contrasena vive solo en el estado de la pantalla:
    // no se guarda ni se registra, y rememberSaveable no se usa para ella (no queda en el estado guardado del sistema).
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verPassword by remember { mutableStateOf(false) }
    // Errores de validacion local: solo se muestran tras intentar entrar y se limpian al editar el campo.
    var errorEmail by remember { mutableStateOf<String?>(null) }
    var errorPassword by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val credenciales = vm.credencialesIncorrectas

    fun entrar() {
        if (vm.loading) return
        val correo = email.trim()
        errorEmail = when {
            correo.isEmpty() -> "Introduce tu correo."
            !correo.contains('@') || correo.startsWith('@') || correo.endsWith('@') -> "Introduce un correo válido."
            else -> null
        }
        errorPassword = if (password.isEmpty()) "Introduce tu contraseña." else null
        if (errorEmail != null || errorPassword != null) { vm.clearError(); return }
        focus.clearFocus()
        vm.login(email = correo, password = password, onSuccess = onLoggedIn)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        // Desplazable y con relleno de teclado: con fuente al 200 % o el teclado abierto todo sigue siendo alcanzable.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = FhEspacio.l),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(FhIcons.Edificio, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.height(FhEspacio.m))

            Text(
                text = "FacilityHub",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Mantenimiento preventivo y cumplimiento documental",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(FhEspacio.xl))

            // Orden fijo de campos (correo, contrasena): la demostracion automatizada los localiza por posicion.
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorEmail = null; if (vm.error != null) vm.clearError() },
                label = { Text("Correo") },
                placeholder = { Text("nombre@empresa.com") },
                singleLine = true,
                enabled = !vm.loading,
                isError = errorEmail != null || credenciales,
                supportingText = errorEmail?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username + ContentType.EmailAddress }
            )

            Spacer(modifier = Modifier.height(FhEspacio.s))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorPassword = null; if (vm.error != null) vm.clearError() },
                label = { Text("Contraseña") },
                singleLine = true,
                enabled = !vm.loading,
                isError = errorPassword != null || credenciales,
                supportingText = errorPassword?.let { { Text(it) } },
                visualTransformation = if (verPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { verPassword = !verPassword }) {
                        Icon(
                            if (verPassword) FhIcons.Ocultar else FhIcons.Mostrar,
                            contentDescription = if (verPassword) "Ocultar contraseña" else "Mostrar contraseña"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onGo = { entrar() }),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password }
            )

            Spacer(modifier = Modifier.height(FhEspacio.l))

            Button(
                onClick = { entrar() },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (vm.loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp, color = LocalContentColor.current,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Accediendo…")
                } else {
                    Text("Iniciar sesión")
                }
            }

            // Un unico aviso, solo ante un error del ultimo intento; desaparece al editar cualquier campo. Ante un fallo de
            // conexion se ofrece reintentar con los mismos datos.
            vm.error?.let { error ->
                Spacer(modifier = Modifier.height(FhEspacio.m))
                if (credenciales) EstadoError(error) else EstadoError(error, enabled = !vm.loading) { entrar() }
            }
            // FH-009 / R3: motivo de la vuelta al login (sesion caducada), solo si no hay un error mas reciente.
            if (vm.error == null) vm.avisoLogin?.let { aviso ->
                Spacer(modifier = Modifier.height(FhEspacio.m))
                Mensaje(aviso, Tono.INFO)
            }
        }
        }
    }
}


/** Tarjeta de lista pulsable con titulo y lineas secundarias (centros, locales, obligaciones). */
@Composable
private fun TarjetaLista(titulo: String, onClick: (() -> Unit)?, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(FhEspacio.l)) {
            Text(text = titulo, style = MaterialTheme.typography.titleMedium)
            contenido()
        }
    }
}

@Composable
private fun TextoSecundario(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CentrosScreen(
    vm: FacilityViewModel,
    onCentro: (CentroDto) -> Unit,
    onBack: () -> Unit,
    // FH-009 / R3: accesos de gestion que el rol no tiene en la barra inferior (ruta, etiqueta). Vacio para el resto.
    accesos: List<Pair<String, String>> = emptyList(),
    onMenu: (String) -> Unit = {}
) {
    LaunchedEffect(Unit) {
        if (vm.centros.isEmpty()) {
            vm.cargarCentros()
        }
    }

    ScreenScaffold(
        title = "Centros",
        subtitle = listOf(vm.userName, rolLegible(vm.role)).filter { it.isNotBlank() }.joinToString(" · "),
        onAction = onBack
    ) {
        // FH-006 / P6-C3 y FH-009 / R3: accesos por rol (la navegacion no sustituye a los controles del backend). Panel,
        // avisos y la pestana propia del rol estan en la barra inferior; aqui quedan los demas accesos de gestion.
        if (accesos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FhEspacio.s), modifier = Modifier.fillMaxWidth()) {
                accesos.forEach { (ruta, etiqueta) ->
                    OutlinedButton(onClick = { onMenu(ruta) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(etiqueta) }
                }
            }
            Spacer(Modifier.height(FhEspacio.m))
        }

        EstadoCargando(vm.loading)
        vm.error?.let { EstadoError(it, enabled = !vm.loading) { vm.cargarCentros() } }

        SectionTitle("Centros comerciales")

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(FhEspacio.s)
        ) {
            items(vm.centros) { centro ->
                TarjetaLista(centro.nombre, onClick = { onCentro(centro) }) {
                    TextoSecundario(listOfNotNull(centro.codigo, centro.ciudad).joinToString(" · "))
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
        EstadoCargando(vm.loading)
        vm.error?.let { EstadoError(it, enabled = !vm.loading) { vm.cargarLocales(centroId) } }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(FhEspacio.s)
        ) {
            items(vm.locales) { local ->
                TarjetaLista(local.nombre ?: local.codigo, onClick = { onLocal(local) }) {
                    TextoSecundario(listOfNotNull(local.codigo, local.planta, local.ubicacion).joinToString(" · "))
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
    onHistorial: () -> Unit = {},
    onBack: () -> Unit
) {
    LaunchedEffect(localId) {
        vm.cargarDetalleLocal(localId)
    }

    ScreenScaffold(
        title = localNombre,
        subtitle = "Detalle del local",
        onAction = onBack,
        scrollable = true
    ) {
        EstadoCargando(vm.loading)
        vm.error?.let { EstadoError(it, enabled = !vm.loading) { vm.cargarDetalleLocal(localId) } }

        vm.inquilino?.let { inquilino ->
            SectionTitle("Inquilino actual")
            Text(
                text = inquilino.nombreComercial ?: inquilino.razonSocial,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            inquilino.emailContacto?.takeIf { it.isNotBlank() }?.let { TextoSecundario(it) }
            Spacer(modifier = Modifier.height(FhEspacio.l))
        }

        SectionTitle("Instalaciones")

        vm.instalaciones.forEach { instalacion ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(instalacion.nombre, modifier = Modifier.weight(1f))
                EstadoChip(etiquetaEstadoInstalacion(instalacion.estado),
                    if (instalacion.estado == "ACTIVA") Tono.EXITO else Tono.NEUTRO)
            }
        }

        Spacer(modifier = Modifier.height(FhEspacio.l))

        // FH-006 / P6-D3-4: historial del local para gestion (el INQUILINO accede desde «Mis obligaciones»).
        if (vm.puedeGestionar) {
            OutlinedButton(onClick = onHistorial, modifier = Modifier.fillMaxWidth()) {
                Icon(FhIcons.Historico, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(FhEspacio.s))
                Text("Historial de mantenimientos")
            }
            Spacer(modifier = Modifier.height(FhEspacio.m))
        }

        SectionTitle("Obligaciones de mantenimiento")

        vm.obligaciones.forEach { obligacion ->
            Box(Modifier.padding(vertical = FhEspacio.xs)) {
                TarjetaLista(obligacion.tipoMantenimiento, onClick = { onObligacion(obligacion) }) {
                    TextoSecundario("Próxima fecha: ${fechaCorta(obligacion.proximaFecha)}")
                    TextoSecundario("Cada ${obligacion.periodicidadMeses} meses")
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
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
            text = "Próxima fecha: ${fechaCorta(proximaFecha)}",
            style = MaterialTheme.typography.bodyLarge
        )

        EstadoCargando(vm.loading)
        vm.error?.let { EstadoError(it) }

        // Crear ejecuciones solo para gestion (ADMIN/EXPLOTACION); el INQUILINO solo consulta.
        if (vm.puedeGestionar) {
            Spacer(modifier = Modifier.height(FhEspacio.m))

            SectionTitle("Nueva ejecución")

            // R7 sustituira este campo por un selector de fecha; la API sigue recibiendo AAAA-MM-DD.
            OutlinedTextField(
                value = fecha,
                onValueChange = { fecha = it },
                label = { Text("Fecha (AAAA-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(FhEspacio.s))

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(FhEspacio.m))

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

        Spacer(modifier = Modifier.height(FhEspacio.l))

        SectionTitle("Histórico / programados")

        vm.mantenimientos.forEach { mantenimiento ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FhEspacio.xs)
            ) {
                Column(
                    modifier = Modifier.padding(FhEspacio.l)
                ) {
                    Text(
                        text = fechaCorta(mantenimiento.fechaProgramada),
                        style = MaterialTheme.typography.titleMedium
                    )
                    FilaChips {
                        EstadoChip(etiquetaEstado(mantenimiento.estado) ?: mantenimiento.estado, tonoEstado(mantenimiento.estado))
                    }

                    mantenimiento.descripcion?.let { descripcion ->
                        Text(text = descripcion, modifier = Modifier.padding(top = FhEspacio.xs))
                    }

                    mantenimiento.fechaRealizacion?.let { fechaRealizacion ->
                        TextoSecundario("Realizado: ${fechaCorta(fechaRealizacion)}")
                    }

                    // Iniciar solo para gestion. FH-006 / P6-C3: "Finalizar" se sustituye por el circuito de
                    // intervenciones (registro, envio y validacion); el cumplimiento lo declara la aprobacion.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FhEspacio.xs)) {
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

                        TextButton(onClick = { onDocumentos(mantenimiento) }) {
                            Text("Documentos")
                        }
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        EstadoCargando(vm.loading)
        vm.error?.let { EstadoError(it) }

        Spacer(modifier = Modifier.height(FhEspacio.l))

        SectionTitle("Documentos adjuntos")

        if (vm.documentos.isEmpty() && !vm.loading && vm.error == null) {
            EstadoVacio("Sin documentos", FhIcons.Documento)
        }

        vm.documentos.forEach { documento ->
            FilaDocumento(
                nombre = documento.nombreOriginal,
                detalle = listOfNotNull(
                    documento.tipoDocumento?.let { etiquetaCategoria(it) },
                    documento.fechaSubida?.let { "Subido ${fechaHora(it)}" }
                ).joinToString(" · "),
                chips = { EstadoChip(etiquetaRevision(documento.estadoRevision), tonoRevision(documento.estadoRevision)) }
            ) {
                documento.observaciones?.let { TextoSecundario(it) }
            }
        }
    }
}

internal fun tonoRevision(e: String?): Tono = when (e) {
    "VALIDO" -> Tono.EXITO
    "RECHAZADO" -> Tono.ERROR
    "PENDIENTE" -> Tono.AVISO
    else -> Tono.NEUTRO
}

/** Fila de documento: icono, nombre en una linea, detalle, chips y accion opcional a la derecha. */
@Composable
internal fun FilaDocumento(
    nombre: String,
    detalle: String = "",
    chips: (@Composable () -> Unit)? = null,
    accion: (@Composable () -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)
    ) {
        Row(modifier = Modifier.padding(start = FhEspacio.m, end = FhEspacio.xs, top = FhEspacio.m, bottom = FhEspacio.m),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.small, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(FhIcons.Documento, contentDescription = null, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(FhEspacio.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                if (detalle.isNotBlank()) Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (chips != null) FilaChips { chips() }
                extra()
            }
            accion?.invoke()
        }
    }
}


// FH-006 / P6-B: trabajos asignados a la empresa del PROVEEDOR. Solo lectura: no hay botones de escritura;
// las acciones futuras (P6-C) se mostraran unicamente si el backend las incluye en accionesPermitidas.
@Composable
fun TrabajosScreen(
    vm: FacilityViewModel,
    onTrabajo: (TrabajoProveedorDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        vm.cargarTrabajos()
    }

    ScreenScaffold(
        title = TITULO_MIS_TRABAJOS,
        subtitle = listOf(vm.userName, rolLegible(vm.role)).filter { it.isNotBlank() }.joinToString(" · "),
        onAction = onBack
    ) {
        EstadoCargando(vm.loading)
        vm.error?.let { EstadoError(it, enabled = !vm.loading) { vm.cargarTrabajos() } }

        if (!vm.loading && vm.trabajos.isEmpty() && vm.error == null) {
            EstadoVacio("No tienes trabajos asignados.", FhIcons.Herramienta)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(FhEspacio.s)
        ) {
            items(vm.trabajos) { trabajo ->
                TarjetaLista(trabajo.tipoMantenimiento, onClick = { onTrabajo(trabajo) }) {
                    TextoSecundario(listOfNotNull(trabajo.centro, trabajo.local ?: "Zona común").joinToString(" · "))
                    // FH-006 / P6-C3: estado de la ultima intervencion de su empresa, delante del estado del mantenimiento.
                    FilaChips {
                        if (trabajo.estadoValidacion != null) {
                            EstadoChip(etiquetaValidacion(trabajo.estadoValidacion), tonoValidacion(trabajo.estadoValidacion),
                                iconoValidacion(trabajo.estadoValidacion))
                        }
                        EstadoChip(etiquetaEstado(trabajo.estado) ?: trabajo.estado, tonoEstado(trabajo.estado))
                    }
                    Text(
                        text = "Programado ${fechaCorta(trabajo.fechaProgramada)}" +
                            if (trabajo.estadoValidacion == null) " · ${etiquetaValidacion(null)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = FhEspacio.xs)
                    )
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
        EstadoCargando(vm.loading)
        vm.error?.let { EstadoError(it, enabled = !vm.loading) { vm.cargarTrabajo(mantenimientoId) } }

        vm.trabajo?.let { trabajo ->
            SectionTitle("Datos del trabajo")
            FilaChips { EstadoChip(etiquetaEstado(trabajo.estado) ?: trabajo.estado, tonoEstado(trabajo.estado)) }
            DatoEtiquetado("Fecha programada", fechaCorta(trabajo.fechaProgramada))
            trabajo.descripcion?.let { DatoEtiquetado("Descripción", it) }

            Spacer(modifier = Modifier.height(FhEspacio.m))
            SectionTitle("Ubicación")
            DatoEtiquetado("Centro", trabajo.centro)
            DatoEtiquetado("Local", trabajo.local ?: "Zona común")
            trabajo.instalacion?.let { DatoEtiquetado("Instalación", it) }

            Spacer(modifier = Modifier.height(FhEspacio.m))
            SectionTitle("Asignación")
            DatoEtiquetado("Responsable", trabajo.responsable)
            trabajo.tecnicoAsignado?.let { DatoEtiquetado("Técnico", it) }
            trabajo.asignadoAt?.let { DatoEtiquetado("Asignado", fechaHora(it)) }

            Spacer(modifier = Modifier.height(FhEspacio.m))

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

/** Par etiqueta/valor alineado (sustituye a "Etiqueta: valor" en texto corrido). */
@Composable
internal fun DatoEtiquetado(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 112.dp, max = 140.dp).padding(end = FhEspacio.s))
        Text(valor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
