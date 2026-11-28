package com.facilityhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.CambiarActivoCentroRequest
import com.facilityhub.app.data.CentroGestionDto
import com.facilityhub.app.data.CrearCentroRequest
import com.facilityhub.app.data.EditarCentroRequest
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-013 / RF-03: gestion de centros (activos e inactivos). ADMIN: alta, edicion, desactivacion y reactivacion.
// EXPLOTACION: consulta. La pestaña «Centros» sigue mostrando solo los centros activos (D8).

internal val FORMATO_CODIGO_CENTRO = Regex("[A-Z0-9-]{2,30}")
internal const val MAX_NOMBRE_CENTRO = 150
internal const val MAX_CIUDAD_CENTRO = 100
internal const val MAX_DIRECCION_CENTRO = 200
internal const val MAX_DESCRIPCION_CENTRO = 2000

private fun cuenta(n: Long, singular: String, plural: String) = "$n ${if (n == 1L) singular else plural}"

@Composable
internal fun AvisoCentro(vm: FacilityViewModel) {
    val aviso = vm.avisoCentro ?: return
    Mensaje(aviso, Tono.EXITO, accion = "Cerrar", onAccion = { vm.consumirAvisoCentro() })
    DisposableEffect(aviso) { onDispose { vm.consumirAvisoCentro() } }
}

/** Todos los centros (activos e inactivos) con su uso. Para EXPLOTACION es solo consulta. */
@Composable
fun CentrosGestionScreen(
    vm: FacilityViewModel,
    onNuevo: () -> Unit,
    onCentro: (CentroGestionDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { vm.cargarCatalogoCentros() }
    var verInactivos by rememberSaveable { mutableStateOf(true) }
    val lista = vm.catalogoCentros.filter { verInactivos || it.activo }

    PantallaLista(title = "Gestionar centros", subtitle = if (vm.esAdmin) "Centros comerciales" else "Centros comerciales (consulta)",
        onAction = onBack) {
        item {
            AvisoCentro(vm)
            EstadoLectura(vm.lecCatalogoCentros) { vm.cargarCatalogoCentros(forzar = true) }
            if (vm.esAdmin) {
                Button(onClick = onNuevo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(FhIcons.Anadir, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Nuevo centro")
                }
            } else {
                Mensaje("Solo un administrador puede registrar, modificar o desactivar centros.", Tono.INFO)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = verInactivos, role = Role.Switch, onValueChange = { verInactivos = it })
            ) {
                Text("Mostrar centros inactivos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = verInactivos, onCheckedChange = null)
            }
            if (vm.lecCatalogoCentros.vacia(lista)) EstadoVacio("No hay centros.")
        }
        items(lista, key = { it.id }) { c ->
            Box(Modifier.padding(vertical = FhEspacio.xs)) { TarjetaCentroGestion(c, if (vm.esAdmin) ({ onCentro(c) }) else null) }
        }
    }
}

@Composable
private fun TarjetaCentroGestion(c: CentroGestionDto, onClick: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(FhEspacio.l)) {
            Text(c.nombre, style = MaterialTheme.typography.titleMedium)
            Text("${c.codigo} · ${c.ciudad}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            c.direccion?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilaChips {
                EstadoChip(if (c.activo) "Activo" else "Inactivo", if (c.activo) Tono.EXITO else Tono.NEUTRO)
                EstadoChip(cuenta(c.localesActivos, "local activo", "locales activos"), Tono.INFO)
                if (c.ocupacionesVigentes > 0) EstadoChip(cuenta(c.ocupacionesVigentes, "ocupación vigente", "ocupaciones vigentes"), Tono.INFO)
                if (c.ocupacionesFuturas > 0) EstadoChip(cuenta(c.ocupacionesFuturas, "ocupación futura", "ocupaciones futuras"), Tono.INFO)
                if (c.mantenimientosAbiertos > 0)
                    EstadoChip(cuenta(c.mantenimientosAbiertos, "mantenimiento abierto", "mantenimientos abiertos"), Tono.AVISO)
            }
        }
    }
}

/**
 * Alta ([centroId] nulo) o edicion de un centro (solo ADMIN). El codigo no se puede cambiar y se guarda la huella recibida:
 * si otro usuario cambia el centro antes de guardar, el backend responde 409 y se ofrece actualizar (D7).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CentroFormScreen(
    vm: FacilityViewModel,
    centroId: Long?,
    onCancelar: () -> Unit,
    onGuardado: () -> Unit
) {
    val edicion = centroId != null
    var cargado by rememberSaveable { mutableStateOf(!edicion) }
    var codigo by rememberSaveable { mutableStateOf("") }
    var nombre by rememberSaveable { mutableStateOf("") }
    var ciudad by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var intentado by rememberSaveable { mutableStateOf(false) }
    var dialogoActivo by rememberSaveable { mutableStateOf(false) }
    val enviando = vm.guardandoCentro
    val errorServidor = vm.errorCentro
    val original = if (edicion) vm.centroEditado?.takeIf { it.id == centroId } else null

    fun rellenar(c: CentroGestionDto) {
        codigo = c.codigo; nombre = c.nombre; ciudad = c.ciudad; direccion = c.direccion.orEmpty()
        descripcion = c.descripcion.orEmpty(); cargado = true
    }

    LaunchedEffect(centroId) {
        vm.limpiarErrorCentro()
        if (centroId != null) vm.cargarCentroEditado(centroId)
    }
    LaunchedEffect(original?.huella) { if (original != null && !cargado) rellenar(original) }

    val codigoNorm = codigo.trim().uppercase()
    val errorCodigo = when {
        edicion -> null
        intentado && codigoNorm.isEmpty() -> "Indica el código."
        codigoNorm.isNotEmpty() && !FORMATO_CODIGO_CENTRO.matches(codigoNorm) ->
            "Entre 2 y 30 caracteres: letras, números y guiones, sin espacios."
        errorServidor?.campo == CampoCentro.CODIGO -> errorServidor.mensaje
        else -> null
    }
    val errorNombre = when {
        intentado && nombre.isBlank() -> "Indica el nombre."
        nombre.trim().length > MAX_NOMBRE_CENTRO -> "Máximo $MAX_NOMBRE_CENTRO caracteres."
        errorServidor?.campo == CampoCentro.NOMBRE -> errorServidor.mensaje
        else -> null
    }
    val errorCiudad = when {
        intentado && ciudad.isBlank() -> "Indica la ciudad."
        ciudad.trim().length > MAX_CIUDAD_CENTRO -> "Máximo $MAX_CIUDAD_CENTRO caracteres."
        else -> null
    }
    val errorDireccion = if (direccion.trim().length > MAX_DIRECCION_CENTRO) "Máximo $MAX_DIRECCION_CENTRO caracteres." else null
    val errorDescripcion = if (descripcion.trim().length > MAX_DESCRIPCION_CENTRO) "Máximo $MAX_DESCRIPCION_CENTRO caracteres." else null

    fun guardar() {
        intentado = true
        if ((!edicion && !FORMATO_CODIGO_CENTRO.matches(codigoNorm)) || nombre.isBlank() || ciudad.isBlank() ||
            nombre.trim().length > MAX_NOMBRE_CENTRO || ciudad.trim().length > MAX_CIUDAD_CENTRO ||
            errorDireccion != null || errorDescripcion != null) return
        if (edicion) {
            val o = original ?: return
            vm.editarCentro(centroId!!, EditarCentroRequest(nombre.trim(), direccion.trim().ifBlank { null }, ciudad.trim(),
                descripcion.trim().ifBlank { null }, o.huella)) { onGuardado() }
        } else {
            vm.crearCentro(CrearCentroRequest(codigoNorm, nombre.trim(), direccion.trim().ifBlank { null }, ciudad.trim(),
                descripcion.trim().ifBlank { null })) { onGuardado() }
        }
    }

    ScreenScaffold(
        title = if (edicion) "Editar centro" else "Nuevo centro",
        subtitle = "Gestionar centros",
        onAction = onCancelar,
        scrollable = true,
        barraInferior = {
            Button(onClick = { guardar() }, enabled = !enviando && cargado, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Guardando…")
                } else Text(if (edicion) "Guardar cambios" else "Guardar centro")
            }
            OutlinedButton(onClick = onCancelar, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
        }
    ) {
        if (edicion) EstadoLectura(vm.lecCentroEditado) { vm.cargarCentroEditado(centroId!!) }
        if (errorServidor != null && errorServidor.campo == null) {
            if (errorServidor.codigo == "DATOS_DESACTUALIZADOS") {
                Mensaje(errorServidor.mensaje, Tono.ERROR, accion = "Actualizar", enabled = !enviando, onAccion = {
                    vm.limpiarErrorCentro(); cargado = false; vm.cargarCentroEditado(centroId!!)
                })
            } else EstadoError(errorServidor.mensaje)
        }
        if (intentado && listOf(errorCodigo, errorNombre, errorCiudad, errorDireccion, errorDescripcion).any { it != null }) {
            Mensaje("Revisa los campos marcados.", Tono.ERROR)
        }
        if (edicion && original != null) {
            FilaChips {
                EstadoChip(if (original.activo) "Activo" else "Inactivo", if (original.activo) Tono.EXITO else Tono.NEUTRO)
                EstadoChip(cuenta(original.localesActivos, "local activo", "locales activos"), Tono.INFO)
            }
        }

        SectionTitle("Datos del centro")
        CampoSimple(
            // No se transforma el texto mientras se escribe (choca con la composicion del teclado): se normaliza al guardar.
            valor = codigo, onValor = { codigo = it; vm.limpiarErrorCentro() }, etiqueta = "Código",
            error = errorCodigo, enabled = !enviando && !edicion, soloLectura = edicion,
            ayuda = when {
                edicion -> "El código no se puede modificar."
                codigoNorm.isNotEmpty() && codigo.trim() != codigoNorm -> "Se guardará como $codigoNorm."
                else -> "Por ejemplo: CC-NORTE. Letras, números y guiones."
            },
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
        )
        CampoTexto(valor = nombre, onValor = { nombre = it; if (errorServidor?.campo == CampoCentro.NOMBRE) vm.limpiarErrorCentro() },
            etiqueta = "Nombre", error = errorNombre, maximo = MAX_NOMBRE_CENTRO, enabled = !enviando)
        CampoTexto(valor = ciudad, onValor = { ciudad = it; if (errorServidor?.campo == CampoCentro.NOMBRE) vm.limpiarErrorCentro() },
            etiqueta = "Ciudad", error = errorCiudad, maximo = MAX_CIUDAD_CENTRO, enabled = !enviando)
        CampoTexto(valor = direccion, onValor = { direccion = it }, etiqueta = "Dirección (opcional)",
            error = errorDireccion, maximo = MAX_DIRECCION_CENTRO, enabled = !enviando)
        CampoTexto(valor = descripcion, onValor = { descripcion = it }, etiqueta = "Descripción (opcional)",
            error = errorDescripcion, maximo = MAX_DESCRIPCION_CENTRO, enabled = !enviando, multilinea = true)

        if (edicion && original != null) {
            Spacer(Modifier.height(FhEspacio.l))
            SectionTitle("Disponibilidad")
            if (!original.activo) {
                Mensaje("Centro inactivo: no aparece en «Centros» y no admite instalaciones, obligaciones ni mantenimientos nuevos.",
                    Tono.INFO)
            }
            OutlinedButton(onClick = { vm.limpiarErrorCentro(); dialogoActivo = true }, enabled = !enviando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (original.activo) "Desactivar centro" else "Reactivar centro")
            }
        }
        Spacer(Modifier.height(FhEspacio.l))
    }

    if (dialogoActivo && original != null) {
        val desactivar = original.activo
        val bloqueado = desactivar &&
            (original.ocupacionesVigentes > 0 || original.ocupacionesFuturas > 0 || original.mantenimientosAbiertos > 0)
        AlertDialog(
            onDismissRequest = { dialogoActivo = false },
            title = { Text(if (!desactivar) "Reactivar centro" else if (bloqueado) "No se puede desactivar" else "Desactivar centro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
                    when {
                        !desactivar -> Text("El centro volverá a mostrarse en «Centros» y admitirá altas nuevas.")
                        bloqueado -> {
                            Text("El centro tiene trabajo sin resolver:")
                            Text("• ${cuenta(original.ocupacionesVigentes, "ocupación vigente", "ocupaciones vigentes")}")
                            if (original.ocupacionesFuturas > 0)
                                Text("• ${cuenta(original.ocupacionesFuturas, "ocupación futura", "ocupaciones futuras")} (todavía sin empezar)")
                            Text("• ${cuenta(original.mantenimientosAbiertos, "mantenimiento abierto", "mantenimientos abiertos")}")
                            Text("Deben finalizar o cerrarse antes de desactivarlo.")
                        }
                        else -> {
                            Text("Resumen del impacto:")
                            Text("• ${cuenta(original.localesActivos, "local activo", "locales activos")}")
                            Text("• ${cuenta(original.instalaciones, "instalación", "instalaciones")}")
                            Text("• ${cuenta(original.obligacionesActivas, "obligación activa", "obligaciones activas")}")
                            Text("Dejará de mostrarse en «Centros» y no admitirá altas nuevas. No se borra nada: locales, " +
                                "obligaciones, instalaciones e historial se conservan y podrás reactivarlo.")
                        }
                    }
                }
            },
            confirmButton = {
                if (!bloqueado) {
                    TextButton(onClick = {
                        dialogoActivo = false
                        vm.cambiarActivoCentro(original.id, CambiarActivoCentroRequest(!desactivar, original.huella,
                            if (desactivar) true else null)) { onGuardado() }
                    }) { Text(if (desactivar) "Desactivar" else "Reactivar") }
                }
            },
            dismissButton = { TextButton(onClick = { dialogoActivo = false }) { Text(if (bloqueado) "Cerrar" else "Cancelar") } }
        )
    }
}
