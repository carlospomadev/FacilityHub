package com.facilityhub.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.onClick
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

        EstadoLectura(vm.lecCentros) { vm.cargarCentros(forzar = true) }

        SectionTitle("Centros comerciales")

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(FhEspacio.s)
        ) {
            items(vm.centros, key = { it.id }) { centro ->
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
    onInstalaciones: () -> Unit = {},
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
        EstadoLectura(vm.lecLocales) { vm.cargarLocales(centroId, forzar = true) }

        // FH-010A / RF-07: instalaciones del centro (zona comun y locales) y su alta, solo gestion.
        if (vm.puedeGestionar) {
            OutlinedButton(onClick = onInstalaciones, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(FhIcons.Herramienta, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(FhEspacio.s))
                Text("Instalaciones del centro")
            }
            Spacer(Modifier.height(FhEspacio.m))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(FhEspacio.s)
        ) {
            items(vm.locales, key = { it.id }) { local ->
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
    // FH-010A / RF-07: alta de instalacion en este local (null = sin acceso, p. ej. sin centro conocido o sin permiso).
    onNuevaInstalacion: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    LaunchedEffect(localId) {
        vm.cargarDetalleLocal(localId)
    }

    PantallaLista(
        title = localNombre,
        subtitle = "Detalle del local",
        onAction = onBack
    ) {
        item {
            AvisoInstalacion(vm)
            EstadoLectura(vm.lecLocal) { vm.cargarDetalleLocal(localId, forzar = true) }
        }

        vm.inquilino?.let { inquilino ->
            item {
                SectionTitle("Inquilino actual")
                Text(
                    text = inquilino.nombreComercial ?: inquilino.razonSocial,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                inquilino.emailContacto?.takeIf { it.isNotBlank() }?.let { TextoSecundario(it) }
                Spacer(modifier = Modifier.height(FhEspacio.l))
            }
        }

        item {
            SectionTitle("Instalaciones")
            if (vm.puedeGestionar && onNuevaInstalacion != null) {
                OutlinedButton(onClick = onNuevaInstalacion, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(FhIcons.Anadir, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Registrar instalación")
                }
            }
            if (vm.lecLocal.vacia(vm.instalaciones)) EstadoVacio("Sin instalaciones registradas.")
        }

        items(vm.instalaciones, key = { "ins-${it.id}" }) { instalacion ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(instalacion.nombre, modifier = Modifier.weight(1f))
                EstadoChip(etiquetaEstadoInstalacion(instalacion.estado),
                    if (instalacion.estado == "ACTIVA") Tono.EXITO else Tono.NEUTRO)
            }
        }

        item {
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
        }

        items(vm.obligaciones, key = { "obl-${it.id}" }) { obligacion ->
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
    // FH-009 / R7: el formulario se conserva al abrir Documentos o Intervenciones y volver.
    var fecha by rememberSaveable(obligacion.id) {
        mutableStateOf(obligacion.proximaFecha)
    }

    var descripcion by rememberSaveable(obligacion.id) {
        mutableStateOf("")
    }

    // La proxima fecha se toma de las obligaciones recargadas tras finalizar.
    val proximaFecha =
        vm.obligaciones.firstOrNull { it.id == obligacion.id }?.proximaFecha
            ?: obligacion.proximaFecha

    var fechaPorDefecto by rememberSaveable(obligacion.id) {
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
        vm.clearError()
        vm.cargarMantenimientos(obligacion.id)
    }

    PantallaLista(
        title = "Mantenimientos",
        subtitle = obligacion.tipoMantenimiento,
        onAction = onBack
    ) {
        item {
            Text(
                text = "Próxima fecha: ${fechaCorta(proximaFecha)}",
                style = MaterialTheme.typography.bodyLarge
            )

            EstadoLectura(vm.lecMantenimientos) { vm.cargarMantenimientos(obligacion.id, forzar = true) }
            // Estado de la ultima operacion (crear, iniciar).
            EstadoCargando(vm.loading)
            vm.error?.let { EstadoError(it) }
        }

        // Crear ejecuciones solo para gestion (ADMIN/EXPLOTACION); el INQUILINO solo consulta.
        if (vm.puedeGestionar) item {
            Spacer(modifier = Modifier.height(FhEspacio.m))

            SectionTitle("Nueva ejecución")

            // FH-009 / R7: selector de fecha; la API sigue recibiendo AAAA-MM-DD.
            CampoFecha(
                valor = fecha,
                onValor = { fecha = it },
                etiqueta = "Fecha programada",
                enabled = !vm.loading
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
                enabled = !vm.loading && fecha.isNotBlank()
            ) {
                Text("Crear mantenimiento")
            }
        }

        item {
            Spacer(modifier = Modifier.height(FhEspacio.l))
            SectionTitle("Histórico / programados")
            if (vm.lecMantenimientos.vacia(vm.mantenimientos)) EstadoVacio("Sin mantenimientos registrados.")
        }

        items(vm.mantenimientos, key = { it.id }) { mantenimiento ->
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
        vm.clearError()
        vm.cargarDocumentos(mantenimientoId)
    }

    PantallaLista(
        title = "Documentos",
        subtitle = titulo,
        onAction = onBack
    ) {
        item {
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

            EstadoLectura(vm.lecDocumentos) { vm.cargarDocumentos(mantenimientoId, forzar = true) }
            // Estado de la subida.
            EstadoCargando(vm.loading)
            vm.error?.let { EstadoError(it) }

            Spacer(modifier = Modifier.height(FhEspacio.l))

            SectionTitle("Documentos adjuntos")

            if (vm.lecDocumentos.vacia(vm.documentos)) {
                EstadoVacio("Sin documentos", FhIcons.Documento)
            }
        }

        items(vm.documentos, key = { it.id }) { documento ->
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
    // FH-009 / R5: controles a todo el ancho de la tarjeta, bajo el documento (p. ej. «Compartir con el inquilino»).
    pie: (@Composable ColumnScope.() -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    // FH-009 / R5: con fuente grande la accion (Descargar) baja bajo el documento en lugar de estrechar su columna.
    val vertical = LocalDensity.current.fontScale >= 1.3f
    val ext = nombre.substringAfterLast('.', "").lowercase()
    val imagen = ext in setOf("jpg", "jpeg", "png")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)
    ) {
        Column(Modifier.padding(start = FhEspacio.m, end = FhEspacio.xs, top = FhEspacio.m, bottom = FhEspacio.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icono segun el tipo de archivo (PDF o imagen), con descripcion accesible.
                val (fondo, contenido) = coloresTono(if (imagen) Tono.INFO else Tono.ERROR)
                Surface(color = fondo, contentColor = contenido, shape = MaterialTheme.shapes.small, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (imagen) FhIcons.Imagen else FhIcons.Documento,
                            contentDescription = when { imagen -> "Imagen"; ext == "pdf" -> "Archivo PDF"; else -> "Archivo" },
                            modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(FhEspacio.m))
                Column(modifier = Modifier.weight(1f).padding(end = FhEspacio.s)) {
                    // Nombre en una linea con puntos suspensivos en medio (conserva extension y fecha); el texto accesible
                    // es siempre el nombre completo. Con fuente grande se permiten varias lineas.
                    Text(nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = if (vertical) 3 else 1, overflow = if (vertical) TextOverflow.Ellipsis else TextOverflow.MiddleEllipsis)
                    if (detalle.isNotBlank()) Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (chips != null) FilaChips { chips() }
                    extra()
                }
                if (!vertical) accion?.invoke()
            }
            if (vertical && accion != null) Box(Modifier.fillMaxWidth().padding(start = 36.dp + FhEspacio.s)) { accion() }
            if (pie != null) Column(Modifier.fillMaxWidth().padding(end = FhEspacio.s), content = pie)
        }
    }
}

/** FH-009 / R5: boton «Descargar» con icono; la descripcion accesible incluye el nombre del documento. */
@Composable
internal fun BotonDescargar(nombre: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).semantics { onClick(label = "Descargar $nombre") { onClick(); true } }
    ) {
        Icon(FhIcons.Descargar, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(FhEspacio.xs))
        Text("Descargar")
    }
}


// FH-006 / P6-B: trabajos asignados a la empresa del PROVEEDOR. Solo lectura: no hay botones de escritura;
// las acciones futuras (P6-C) se mostraran unicamente si el backend las incluye en accionesPermitidas.
// FH-009 / R6: «Mis trabajos» del PROVEEDOR con pestanas por fase del trabajo. La clasificacion se deriva de los estados
// reales de la API (no se inventan estados):
//  - Correccion: la ultima intervencion de su empresa esta en REQUIERE_CORRECCION.
//  - Enviados: intervencion PENDIENTE_VALIDACION o APROBADO, o mantenimiento FINALIZADO/CANCELADO (nada que hacer).
//  - Por hacer: el resto (sin intervencion, BORRADOR o CERRADO_SIN_CUMPLIMIENTO: el intento se cerro y el mantenimiento
//    sigue pendiente).
internal enum class FaseTrabajo(val etiqueta: String) { POR_HACER("Por hacer"), CORRECCION("Corrección"), ENVIADOS("Enviados") }

internal fun faseTrabajo(t: TrabajoProveedorDto): FaseTrabajo = when {
    t.estadoValidacion == "REQUIERE_CORRECCION" -> FaseTrabajo.CORRECCION
    t.estadoValidacion == "PENDIENTE_VALIDACION" || t.estadoValidacion == "APROBADO" ||
        t.estado == "FINALIZADO" || t.estado == "CANCELADO" -> FaseTrabajo.ENVIADOS
    else -> FaseTrabajo.POR_HACER
}

@Composable
fun TrabajosScreen(
    vm: FacilityViewModel,
    flujo: FlujoViewModel,
    onTrabajo: (TrabajoProveedorDto) -> Unit,
    onIntervencion: (Long) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { vm.cargarTrabajos() }
    val porFase = vm.trabajos.groupBy { faseTrabajo(it) }
    val correcciones = porFase[FaseTrabajo.CORRECCION].orEmpty()
    // Motivo de cada correccion (endpoint existente de la intervencion; se refresca al mostrarse la lista).
    val idsCorreccion = correcciones.mapNotNull { it.intervencionId }
    LaunchedEffect(idsCorreccion) { if (idsCorreccion.isNotEmpty()) flujo.cargarComentariosCorreccion(idsCorreccion) }

    // Pestana elegida (se conserva al abrir un trabajo y volver). Hasta que el usuario elige, se abre la de correccion si
    // hay alguna (lo mas urgente) y si no «Por hacer».
    var elegida by rememberSaveable { mutableStateOf(-1) }
    val pestana = if (elegida >= 0) elegida else if (correcciones.isNotEmpty()) FaseTrabajo.CORRECCION.ordinal else FaseTrabajo.POR_HACER.ordinal
    val fase = FaseTrabajo.entries[pestana]
    val lista = porFase[fase].orEmpty()

    ScreenScaffold(
        title = TITULO_MIS_TRABAJOS,
        subtitle = listOf(vm.userName, rolLegible(vm.role)).filter { it.isNotBlank() }.joinToString(" · "),
        onAction = onBack
    ) {
        PestanasInternas(
            titulos = FaseTrabajo.entries.map { f -> "${f.etiqueta} · ${porFase[f].orEmpty().size}" },
            seleccionada = pestana,
            onSeleccionar = { elegida = it }
        )
        Spacer(Modifier.height(FhEspacio.s))
        EstadoLectura(vm.lecTrabajos) { vm.cargarTrabajos(forzar = true) }
        if (vm.lecTrabajos.vacia(lista)) {
            EstadoVacio(
                when (fase) {
                    FaseTrabajo.POR_HACER -> if (vm.trabajos.isEmpty()) "No tienes trabajos asignados." else "No tienes trabajos pendientes de hacer."
                    FaseTrabajo.CORRECCION -> "No tienes trabajos devueltos para corregir."
                    FaseTrabajo.ENVIADOS -> "Todavía no has enviado trabajos a validación."
                },
                if (fase == FaseTrabajo.CORRECCION) FhIcons.Hecho else FhIcons.Herramienta
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(FhEspacio.s), contentPadding = PaddingValues(bottom = FhEspacio.l)) {
            items(lista, key = { it.mantenimientoId }) { t ->
                TarjetaTrabajoProveedor(t, flujo.comentariosCorreccion[t.intervencionId], onAbrir = { onTrabajo(t) },
                    onCorregir = t.intervencionId?.let { id -> { onIntervencion(id) } })
            }
        }
    }
}

/** Tarjeta de trabajo del proveedor: estado y accion que corresponde delante; la correccion se destaca con texto e icono. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaTrabajoProveedor(
    t: TrabajoProveedorDto,
    correccion: ValidacionDto?,
    onAbrir: () -> Unit,
    onCorregir: (() -> Unit)?
) {
    val fase = faseTrabajo(t)
    val accion = when (fase) {
        FaseTrabajo.CORRECCION -> "Abrir para corregir y enviar a validación"
        FaseTrabajo.POR_HACER -> if (t.estadoValidacion == "BORRADOR") "Abrir para completar y enviar" else "Abrir para registrar la intervención"
        FaseTrabajo.ENVIADOS -> "Ver el trabajo"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (fase == FaseTrabajo.CORRECCION) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = accion, role = Role.Button, onClick = onAbrir)
    ) {
        Column(Modifier.padding(FhEspacio.m), verticalArrangement = Arrangement.spacedBy(FhEspacio.xs)) {
            // 1. Estado (lo que importa al tecnico) delante.
            FilaChips {
                if (t.estadoValidacion != null) {
                    EstadoChip(etiquetaValidacion(t.estadoValidacion), tonoValidacion(t.estadoValidacion), iconoValidacion(t.estadoValidacion))
                }
                EstadoChip(etiquetaEstado(t.estado) ?: t.estado, tonoEstado(t.estado))
            }
            // 2. Nombre y ubicacion.
            Row(verticalAlignment = Alignment.Top) {
                Text(t.tipoMantenimiento, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(FhIcons.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(listOfNotNull(t.centro, t.local ?: "Zona común").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            t.instalacion?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                "Programado ${fechaCorta(t.fechaProgramada)}" + if (t.estadoValidacion == null) " · ${etiquetaValidacion(null)}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 3. Correccion: motivo de Explotacion visible y acceso directo para corregir.
            if (fase == FaseTrabajo.CORRECCION) {
                val (fondo, contenido) = coloresTono(Tono.ERROR)
                Surface(color = fondo, contentColor = contenido, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(FhEspacio.m)) {
                        Icon(FhIcons.Editar, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(FhEspacio.s))
                        Column {
                            Text("Motivo de la corrección", style = MaterialTheme.typography.labelLarge)
                            Text(correccion?.comentario?.let { "«$it»" } ?: "Abre la intervención para ver el motivo.",
                                style = MaterialTheme.typography.bodyMedium)
                            correccion?.let { v ->
                                Text(listOfNotNull("Explotación", v.validadaPor, v.fecha?.let { fechaHora(it) }).distinct().joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (onCorregir != null) {
                    Button(onClick = onCorregir, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(FhIcons.Editar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(FhEspacio.s))
                        Text("Corregir y reenviar")
                    }
                }
            } else {
                Text(accion, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
        // Reintentar recarga todo lo que muestra la pantalla (trabajo e intervenciones), no solo la primera lectura.
        EstadoLectura(vm.lecTrabajo) { vm.cargarTrabajo(mantenimientoId); flujo?.cargarIntervencionesMantenimiento(mantenimientoId) }

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
    // FH-009 / R5: etiqueta y valor se leen juntos con TalkBack; con fuente grande la etiqueta va encima del valor para no
    // partir palabras en una columna estrecha.
    val apilado = LocalDensity.current.fontScale >= 1.3f
    val m = Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {}
    if (apilado) {
        Column(m) {
            Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Row(m) {
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 112.dp, max = 140.dp).padding(end = FhEspacio.s))
            Text(valor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
