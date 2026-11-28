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
import com.facilityhub.app.data.CambiarActivoLocalRequest
import com.facilityhub.app.data.CrearLocalRequest
import com.facilityhub.app.data.EditarLocalRequest
import com.facilityhub.app.data.LocalGestionDto
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-014 / RF-04: gestion de los locales de un centro (activos e inactivos). ADMIN: alta, edicion, desactivacion y
// reactivacion. EXPLOTACION: consulta. La lista «Locales» del centro sigue mostrando solo los activos (D8).

internal const val MAX_CODIGO_LOCAL = 40
internal const val MAX_NOMBRE_LOCAL = 120
internal const val MAX_PLANTA_LOCAL = 40
internal const val MAX_UBICACION_LOCAL = 150

private fun cuentaLocal(n: Long, singular: String, plural: String) = "$n ${if (n == 1L) singular else plural}"

@Composable
internal fun AvisoLocal(vm: FacilityViewModel) {
    val aviso = vm.avisoLocal ?: return
    Mensaje(aviso, Tono.EXITO, accion = "Cerrar", onAccion = { vm.consumirAvisoLocal() })
    DisposableEffect(aviso) { onDispose { vm.consumirAvisoLocal() } }
}

/** Todos los locales del centro (activos e inactivos) con su uso. Para EXPLOTACION es solo consulta. */
@Composable
fun LocalesGestionScreen(
    vm: FacilityViewModel,
    centroId: Long,
    centroNombre: String,
    onNuevo: () -> Unit,
    onLocal: (LocalGestionDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(centroId) { vm.cargarCatalogoLocales(centroId) }
    var verInactivos by rememberSaveable { mutableStateOf(true) }
    val lista = vm.catalogoLocales.filter { it.centroId == centroId && (verInactivos || it.activo) }

    PantallaLista(title = "Gestionar locales", subtitle = if (vm.esAdmin) centroNombre else "$centroNombre (consulta)",
        onAction = onBack) {
        item {
            AvisoLocal(vm)
            EstadoLectura(vm.lecCatalogoLocales) { vm.cargarCatalogoLocales(centroId, forzar = true) }
            if (vm.esAdmin) {
                Button(onClick = onNuevo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(FhIcons.Anadir, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Nuevo local")
                }
            } else {
                Mensaje("Solo un administrador puede registrar, modificar o desactivar locales.", Tono.INFO)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = verInactivos, role = Role.Switch, onValueChange = { verInactivos = it })
            ) {
                Text("Mostrar locales inactivos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = verInactivos, onCheckedChange = null)
            }
            if (vm.lecCatalogoLocales.vacia(lista)) EstadoVacio("No hay locales.")
        }
        items(lista, key = { it.id }) { l ->
            Box(Modifier.padding(vertical = FhEspacio.xs)) { TarjetaLocalGestion(l, if (vm.esAdmin) ({ onLocal(l) }) else null) }
        }
    }
}

@Composable
private fun TarjetaLocalGestion(l: LocalGestionDto, onClick: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(FhEspacio.l)) {
            // Registros antiguos pueden no tener nombre: se muestra el codigo (D3).
            Text(l.nombre ?: l.codigo, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(l.codigo, l.planta, l.ubicacion).joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilaChips {
                EstadoChip(if (l.activo) "Activo" else "Inactivo", if (l.activo) Tono.EXITO else Tono.NEUTRO)
                if (l.instalaciones > 0) EstadoChip(cuentaLocal(l.instalaciones, "instalación", "instalaciones"), Tono.INFO)
                if (l.ocupacionesVigentes > 0) EstadoChip("Ocupado", Tono.INFO)
                if (l.ocupacionesFuturas > 0)
                    EstadoChip(cuentaLocal(l.ocupacionesFuturas, "ocupación futura", "ocupaciones futuras"), Tono.INFO)
                if (l.mantenimientosAbiertos > 0)
                    EstadoChip(cuentaLocal(l.mantenimientosAbiertos, "mantenimiento abierto", "mantenimientos abiertos"), Tono.AVISO)
            }
        }
    }
}

/**
 * Alta ([localId] nulo) o edicion de un local (solo ADMIN). El centro y el codigo no se pueden cambiar y se guarda la
 * huella recibida: si otro usuario cambia el local antes de guardar, el backend responde 409 y se ofrece actualizar (D7).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalFormScreen(
    vm: FacilityViewModel,
    centroId: Long,
    centroNombre: String,
    localId: Long?,
    onCancelar: () -> Unit,
    onGuardado: () -> Unit
) {
    val edicion = localId != null
    var cargado by rememberSaveable { mutableStateOf(!edicion) }
    var codigo by rememberSaveable { mutableStateOf("") }
    var nombre by rememberSaveable { mutableStateOf("") }
    var planta by rememberSaveable { mutableStateOf("") }
    var ubicacion by rememberSaveable { mutableStateOf("") }
    var intentado by rememberSaveable { mutableStateOf(false) }
    var dialogoActivo by rememberSaveable { mutableStateOf(false) }
    val enviando = vm.guardandoLocal
    val errorServidor = vm.errorLocal
    val original = if (edicion) vm.localEditado?.takeIf { it.id == localId } else null

    fun rellenar(l: LocalGestionDto) {
        codigo = l.codigo; nombre = l.nombre.orEmpty(); planta = l.planta.orEmpty(); ubicacion = l.ubicacion.orEmpty()
        cargado = true
    }

    LaunchedEffect(localId) {
        vm.limpiarErrorLocal()
        if (localId != null) vm.cargarLocalEditado(localId)
    }
    LaunchedEffect(original?.huella) { if (original != null && !cargado) rellenar(original) }

    val codigoNorm = codigo.trim().uppercase()
    val errorCodigo = when {
        edicion -> null
        intentado && codigoNorm.isEmpty() -> "Indica el código."
        codigoNorm.length > MAX_CODIGO_LOCAL -> "Máximo $MAX_CODIGO_LOCAL caracteres."
        errorServidor?.campo == CampoLocal.CODIGO -> errorServidor.mensaje
        else -> null
    }
    val errorNombre = when {
        intentado && nombre.isBlank() -> "Indica el nombre."
        nombre.trim().length > MAX_NOMBRE_LOCAL -> "Máximo $MAX_NOMBRE_LOCAL caracteres."
        else -> null
    }
    val errorPlanta = if (planta.trim().length > MAX_PLANTA_LOCAL) "Máximo $MAX_PLANTA_LOCAL caracteres." else null
    val errorUbicacion = if (ubicacion.trim().length > MAX_UBICACION_LOCAL) "Máximo $MAX_UBICACION_LOCAL caracteres." else null

    fun guardar() {
        intentado = true
        if ((!edicion && (codigoNorm.isEmpty() || codigoNorm.length > MAX_CODIGO_LOCAL)) || nombre.isBlank() ||
            nombre.trim().length > MAX_NOMBRE_LOCAL || errorPlanta != null || errorUbicacion != null) return
        if (edicion) {
            val o = original ?: return
            vm.editarLocal(localId!!, EditarLocalRequest(nombre.trim(), planta.trim().ifBlank { null },
                ubicacion.trim().ifBlank { null }, o.huella)) { onGuardado() }
        } else {
            vm.crearLocal(centroId, CrearLocalRequest(codigoNorm, nombre.trim(), planta.trim().ifBlank { null },
                ubicacion.trim().ifBlank { null })) { onGuardado() }
        }
    }

    ScreenScaffold(
        title = if (edicion) "Editar local" else "Nuevo local",
        subtitle = "Gestionar locales",
        onAction = onCancelar,
        scrollable = true,
        barraInferior = {
            Button(onClick = { guardar() }, enabled = !enviando && cargado, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Guardando…")
                } else Text(if (edicion) "Guardar cambios" else "Guardar local")
            }
            OutlinedButton(onClick = onCancelar, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
        }
    ) {
        if (edicion) EstadoLectura(vm.lecLocalEditado) { vm.cargarLocalEditado(localId!!) }
        if (errorServidor != null && errorServidor.campo == null) {
            if (errorServidor.codigo == "DATOS_DESACTUALIZADOS") {
                Mensaje(errorServidor.mensaje, Tono.ERROR, accion = "Actualizar", enabled = !enviando, onAccion = {
                    vm.limpiarErrorLocal(); cargado = false; vm.cargarLocalEditado(localId!!)
                })
            } else EstadoError(errorServidor.mensaje)
        }
        if (intentado && listOf(errorCodigo, errorNombre, errorPlanta, errorUbicacion).any { it != null }) {
            Mensaje("Revisa los campos marcados.", Tono.ERROR)
        }
        if (edicion && original != null) {
            FilaChips {
                EstadoChip(if (original.activo) "Activo" else "Inactivo", if (original.activo) Tono.EXITO else Tono.NEUTRO)
                EstadoChip(cuentaLocal(original.instalaciones, "instalación", "instalaciones"), Tono.INFO)
                EstadoChip(cuentaLocal(original.obligacionesActivas, "obligación activa", "obligaciones activas"), Tono.INFO)
            }
        }

        SectionTitle("Datos del local")
        // D6: el centro se muestra como dato no editable.
        CampoSimple(valor = original?.centroNombre ?: centroNombre, onValor = {}, etiqueta = "Centro", error = null,
            enabled = false, soloLectura = true, ayuda = "El centro de un local no se puede cambiar.",
            teclado = KeyboardOptions.Default)
        CampoSimple(
            // No se transforma el texto mientras se escribe (choca con la composicion del teclado): se normaliza al guardar.
            valor = codigo, onValor = { codigo = it; vm.limpiarErrorLocal() }, etiqueta = "Código",
            error = errorCodigo, enabled = !enviando && !edicion, soloLectura = edicion,
            ayuda = when {
                edicion -> "El código no se puede modificar."
                codigoNorm.isNotEmpty() && codigo.trim() != codigoNorm -> "Se guardará como $codigoNorm."
                else -> "Por ejemplo: L-101. Único dentro del centro."
            },
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
        )
        CampoTexto(valor = nombre, onValor = { nombre = it }, etiqueta = "Nombre", error = errorNombre,
            maximo = MAX_NOMBRE_LOCAL, enabled = !enviando)
        CampoTexto(valor = planta, onValor = { planta = it }, etiqueta = "Planta (opcional)", error = errorPlanta,
            maximo = MAX_PLANTA_LOCAL, enabled = !enviando)
        CampoTexto(valor = ubicacion, onValor = { ubicacion = it }, etiqueta = "Ubicación (opcional)", error = errorUbicacion,
            maximo = MAX_UBICACION_LOCAL, enabled = !enviando)

        if (edicion && original != null) {
            Spacer(Modifier.height(FhEspacio.l))
            SectionTitle("Disponibilidad")
            if (!original.activo) {
                Mensaje("Local inactivo: no aparece en «Locales» y no admite instalaciones, obligaciones ni mantenimientos nuevos.",
                    Tono.INFO)
            }
            if (!original.activo && !original.centroActivo) {
                Mensaje("El centro está inactivo: reactívalo antes de reactivar este local.", Tono.AVISO)
            }
            OutlinedButton(onClick = { vm.limpiarErrorLocal(); dialogoActivo = true },
                enabled = !enviando && (original.activo || original.centroActivo),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (original.activo) "Desactivar local" else "Reactivar local")
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
            title = { Text(if (!desactivar) "Reactivar local" else if (bloqueado) "No se puede desactivar" else "Desactivar local") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
                    when {
                        !desactivar -> Text("El local volverá a mostrarse en «Locales» y admitirá altas nuevas.")
                        bloqueado -> {
                            Text("El local está en uso:")
                            Text("• ${cuentaLocal(original.ocupacionesVigentes, "ocupación vigente", "ocupaciones vigentes")}")
                            if (original.ocupacionesFuturas > 0)
                                Text("• ${cuentaLocal(original.ocupacionesFuturas, "ocupación futura", "ocupaciones futuras")} (todavía sin empezar)")
                            Text("• ${cuentaLocal(original.mantenimientosAbiertos, "mantenimiento abierto", "mantenimientos abiertos")}")
                            Text("Deben terminar o cerrarse antes de desactivarlo.")
                        }
                        else -> {
                            Text("Resumen del impacto:")
                            Text("• ${cuentaLocal(original.instalaciones, "instalación", "instalaciones")}")
                            Text("• ${cuentaLocal(original.obligacionesActivas, "obligación activa", "obligaciones activas")}")
                            Text("• ${cuentaLocal(original.mantenimientos, "mantenimiento en el historial", "mantenimientos en el historial")}")
                            Text("• ${cuentaLocal(original.ocupaciones, "ocupación registrada", "ocupaciones registradas")}")
                            Text("Dejará de mostrarse en «Locales» y no admitirá altas nuevas. No se borra nada: instalaciones, " +
                                "obligaciones, documentos e historial se conservan y podrás reactivarlo. Las obligaciones " +
                                "activas pueden seguir generando avisos de vencimiento.")
                        }
                    }
                }
            },
            confirmButton = {
                if (!bloqueado) {
                    TextButton(onClick = {
                        dialogoActivo = false
                        vm.cambiarActivoLocal(original.id, CambiarActivoLocalRequest(!desactivar, original.huella,
                            if (desactivar) true else null)) { onGuardado() }
                    }) { Text(if (desactivar) "Desactivar" else "Reactivar") }
                }
            },
            dismissButton = { TextButton(onClick = { dialogoActivo = false }) { Text(if (bloqueado) "Cerrar" else "Cancelar") } }
        )
    }
}
