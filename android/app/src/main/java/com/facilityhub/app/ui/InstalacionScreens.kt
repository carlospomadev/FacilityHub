package com.facilityhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.CrearInstalacionRequest
import com.facilityhub.app.data.InstalacionCentroDto
import com.facilityhub.app.data.LocalDto
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-010A / RF-07: consulta de instalaciones del centro y formulario de alta (solo gestion).

internal const val MAX_NOMBRE_INSTALACION = 120
internal const val MAX_TIPO_INSTALACION = 80
internal const val MAX_DESCRIPCION_INSTALACION = 2000
internal val ESTADOS_INSTALACION = listOf("ACTIVA", "INACTIVA", "FUERA_SERVICIO")

/** Confirmacion de alta: se muestra una vez en la lista de destino y se descarta al salir de ella. */
@Composable
internal fun AvisoInstalacion(vm: FacilityViewModel) {
    val aviso = vm.avisoInstalacion ?: return
    Mensaje(aviso, Tono.EXITO, accion = "Cerrar", onAccion = { vm.consumirAvisoInstalacion() })
    DisposableEffect(aviso) { onDispose { vm.consumirAvisoInstalacion() } }
}

/** Instalaciones del centro agrupadas por ubicacion (zona comun primero, luego por local), con acceso al alta. */
@Composable
fun InstalacionesCentroScreen(
    vm: FacilityViewModel,
    centroId: Long,
    centroNombre: String,
    onNueva: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(centroId) { vm.cargarInstalacionesCentro(centroId) }
    val grupos = vm.instalacionesCentro.groupBy { it.localId }

    PantallaLista(title = "Instalaciones", subtitle = centroNombre, onAction = onBack) {
        item {
            AvisoInstalacion(vm)
            EstadoLectura(vm.lecInstalacionesCentro) { vm.cargarInstalacionesCentro(centroId, forzar = true) }
            Button(onClick = onNueva, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(FhIcons.Anadir, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(FhEspacio.s))
                Text("Registrar instalación")
            }
            Spacer(Modifier.height(FhEspacio.m))
            if (vm.lecInstalacionesCentro.vacia(vm.instalacionesCentro)) EstadoVacio("Este centro no tiene instalaciones registradas.")
        }
        grupos.forEach { (localId, lista) ->
            item(key = "grupo-${localId ?: "zc"}") { SectionTitle(ubicacionInstalacion(lista.first())) }
            items(lista, key = { it.id }) { instalacion -> FilaInstalacion(instalacion) }
        }
    }
}

@Composable
private fun FilaInstalacion(i: InstalacionCentroDto) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)) {
        Column(Modifier.weight(1f)) {
            Text(i.nombre, style = MaterialTheme.typography.bodyLarge)
            Text(i.tipo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EstadoChip(etiquetaEstadoInstalacion(i.estado), if (i.estado == "ACTIVA") Tono.EXITO else Tono.NEUTRO)
    }
}

internal fun ubicacionInstalacion(i: InstalacionCentroDto): String =
    if (i.localId == null) "Zona común" else listOfNotNull(i.localCodigo, i.localNombre).joinToString(" · ")

/**
 * Formulario de alta. El centro lo fija la navegacion; la ubicacion (zona comun o un local del centro) se elige en la
 * lista desplegable, preseleccionada con [localInicial]. Los campos se conservan ante errores y al girar la pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalacionFormScreen(
    vm: FacilityViewModel,
    centroId: Long,
    centroNombre: String,
    localInicial: Long?,
    onCancelar: () -> Unit,
    onCreada: (InstalacionCentroDto) -> Unit
) {
    var localId by rememberSaveable { mutableStateOf(localInicial) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var tipo by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var estado by rememberSaveable { mutableStateOf("ACTIVA") }
    var intentado by rememberSaveable { mutableStateOf(false) }
    val enviando = vm.guardandoInstalacion
    val errorServidor = vm.errorInstalacion

    LaunchedEffect(centroId) {
        vm.limpiarErrorInstalacion()
        vm.cargarLocales(centroId)
    }

    val errorNombre = when {
        intentado && nombre.isBlank() -> "Indica el nombre de la instalación."
        nombre.trim().length > MAX_NOMBRE_INSTALACION -> "Máximo $MAX_NOMBRE_INSTALACION caracteres."
        errorServidor?.duplicado == true -> errorServidor.mensaje
        else -> null
    }
    val errorTipo = when {
        intentado && tipo.isBlank() -> "Indica el tipo de instalación."
        tipo.trim().length > MAX_TIPO_INSTALACION -> "Máximo $MAX_TIPO_INSTALACION caracteres."
        else -> null
    }
    val errorDescripcion = if (descripcion.trim().length > MAX_DESCRIPCION_INSTALACION)
        "Máximo $MAX_DESCRIPCION_INSTALACION caracteres." else null
    val localesCentro = if (vm.lecLocales.clave == centroId) vm.locales else emptyList()
    val localValido = localId == null || localesCentro.any { it.id == localId } || localId == localInicial

    fun guardar() {
        intentado = true
        if (nombre.isBlank() || tipo.isBlank() || errorNombre != null && errorServidor?.duplicado != true ||
            errorTipo != null || errorDescripcion != null || !localValido) return
        vm.registrarInstalacion(
            centroId,
            CrearInstalacionRequest(localId, nombre.trim(), tipo.trim(), descripcion.trim().ifBlank { null }, estado),
            onCreada
        )
    }

    ScreenScaffold(
        title = "Nueva instalación",
        subtitle = centroNombre,
        onAction = onCancelar,
        scrollable = true,
        barraInferior = {
            Button(
                onClick = { guardar() },
                enabled = !enviando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Guardando…")
                } else {
                    Text("Guardar instalación")
                }
            }
            OutlinedButton(onClick = onCancelar, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
        }
    ) {
        EstadoLectura(vm.lecLocales) { vm.cargarLocales(centroId, forzar = true) }
        if (errorServidor != null && !errorServidor.duplicado) EstadoError(errorServidor.mensaje)
        if (intentado && (errorNombre != null || errorTipo != null || errorDescripcion != null)) {
            Mensaje("Revisa los campos marcados.", Tono.ERROR)
        }

        SectionTitle("Ubicación")
        SelectorUbicacion(
            localId = localId,
            locales = localesCentro,
            enabled = !enviando,
            onLocal = { localId = it; vm.limpiarErrorInstalacion() }
        )
        Text(
            "La zona común es una instalación del centro que no pertenece a ningún local.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = FhEspacio.xs)
        )

        Spacer(Modifier.height(FhEspacio.l))
        SectionTitle("Datos de la instalación")
        CampoTexto(
            valor = nombre, onValor = { nombre = it; if (errorServidor?.duplicado == true) vm.limpiarErrorInstalacion() },
            etiqueta = "Nombre", error = errorNombre, maximo = MAX_NOMBRE_INSTALACION, enabled = !enviando
        )
        CampoTexto(
            valor = tipo, onValor = { tipo = it }, etiqueta = "Tipo", error = errorTipo, maximo = MAX_TIPO_INSTALACION,
            enabled = !enviando, ayuda = "Por ejemplo: PCI, climatización o baja tensión."
        )
        CampoTexto(
            valor = descripcion, onValor = { descripcion = it }, etiqueta = "Descripción (opcional)", error = errorDescripcion,
            maximo = MAX_DESCRIPCION_INSTALACION, enabled = !enviando, multilinea = true
        )

        Spacer(Modifier.height(FhEspacio.m))
        SectionTitle("Estado")
        Column(Modifier.selectableGroup()) {
            ESTADOS_INSTALACION.forEach { valor ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = estado == valor, enabled = !enviando, role = Role.RadioButton,
                            onClick = { estado = valor })
                ) {
                    RadioButton(selected = estado == valor, onClick = null, enabled = !enviando)
                    Spacer(Modifier.width(FhEspacio.s))
                    Text(etiquetaEstadoInstalacion(valor), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(FhEspacio.l))
    }
}

@Composable
private fun CampoTexto(
    valor: String,
    onValor: (String) -> Unit,
    etiqueta: String,
    error: String?,
    maximo: Int,
    enabled: Boolean,
    ayuda: String? = null,
    multilinea: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(etiqueta) },
        isError = error != null,
        enabled = enabled,
        singleLine = !multilinea,
        minLines = if (multilinea) 3 else 1,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (multilinea) ImeAction.Default else ImeAction.Next
        ),
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                Text(error ?: ayuda.orEmpty(), modifier = Modifier.weight(1f))
                Text("${valor.trim().length}/$maximo")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FhEspacio.xs)
            .then(if (error != null) Modifier.semantics { error(error) } else Modifier)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorUbicacion(localId: Long?, locales: List<LocalDto>, enabled: Boolean, onLocal: (Long?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val texto = if (localId == null) "Zona común"
        else locales.firstOrNull { it.id == localId }?.let { etiquetaLocal(it) } ?: "Local seleccionado"
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { if (enabled) abierto = it }) {
        OutlinedTextField(
            value = texto,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Zona común o local") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Zona común") }, onClick = { onLocal(null); abierto = false })
            locales.forEach { local ->
                DropdownMenuItem(text = { Text(etiquetaLocal(local)) }, onClick = { onLocal(local.id); abierto = false })
            }
        }
    }
}

private fun etiquetaLocal(l: LocalDto): String = listOfNotNull(l.codigo, l.nombre).joinToString(" · ")
