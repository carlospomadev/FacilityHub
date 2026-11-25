package com.facilityhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.CambiarActivoTipoRequest
import com.facilityhub.app.data.CrearTipoRequest
import com.facilityhub.app.data.EditarTipoRequest
import com.facilityhub.app.data.TipoGestionDto
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-012 / RF-08: gestion del catalogo de tipos de mantenimiento. ADMIN: alta, edicion y activacion. EXPLOTACION: consulta.

internal const val MIN_PERIODICIDAD = 1
internal const val MAX_PERIODICIDAD = 120
internal val FORMATO_CODIGO_TIPO = Regex("[A-Z0-9-]{2,40}")

internal fun etiquetaRegla(r: String?): String = when (r) {
    "DESDE_EJECUCION" -> "Desde la fecha de ejecución"
    "DESDE_PROGRAMADA" -> "Desde la fecha programada"
    else -> r.orEmpty()
}

private fun cadaMeses(n: Int) = "Cada $n ${if (n == 1) "mes" else "meses"}"
private fun obligacionesTexto(n: Long) = if (n == 1L) "1 obligación activa" else "$n obligaciones activas"

@Composable
internal fun AvisoTipo(vm: FacilityViewModel) {
    val aviso = vm.avisoTipo ?: return
    Mensaje(aviso, Tono.EXITO, accion = "Cerrar", onAccion = { vm.consumirAvisoTipo() })
    DisposableEffect(aviso) { onDispose { vm.consumirAvisoTipo() } }
}

/** Catalogo completo (activos e inactivos). Para EXPLOTACION es solo consulta. */
@Composable
fun TiposMantenimientoScreen(
    vm: FacilityViewModel,
    onNuevo: () -> Unit,
    onTipo: (TipoGestionDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { vm.cargarCatalogoTipos() }
    var verInactivos by rememberSaveable { mutableStateOf(true) }
    val lista = vm.catalogoTipos.filter { verInactivos || it.activo }

    PantallaLista(title = "Tipos de mantenimiento", subtitle = if (vm.esAdmin) "Catálogo" else "Catálogo (consulta)", onAction = onBack) {
        item {
            AvisoTipo(vm)
            EstadoLectura(vm.lecCatalogoTipos) { vm.cargarCatalogoTipos(forzar = true) }
            if (vm.esAdmin) {
                Button(onClick = onNuevo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(FhIcons.Anadir, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Nuevo tipo")
                }
            } else {
                Mensaje("Solo un administrador puede registrar o modificar tipos de mantenimiento.", Tono.INFO)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = verInactivos, role = Role.Switch, onValueChange = { verInactivos = it })
            ) {
                Text("Mostrar tipos inactivos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = verInactivos, onCheckedChange = null)
            }
            if (vm.lecCatalogoTipos.vacia(lista)) EstadoVacio("No hay tipos de mantenimiento.")
        }
        items(lista, key = { it.id }) { t ->
            Box(Modifier.padding(vertical = FhEspacio.xs)) { TarjetaTipo(t, if (vm.esAdmin) ({ onTipo(t) }) else null) }
        }
    }
}

@Composable
private fun TarjetaTipo(t: TipoGestionDto, onClick: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(FhEspacio.l)) {
            Text(t.nombre, style = MaterialTheme.typography.titleMedium)
            Text("${t.codigo} · ${cadaMeses(t.periodicidadMeses)}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(etiquetaRegla(t.reglaRecalculo) + " · " +
                if (t.requiereDocumento) "Requiere evidencia o certificado" else "Sin documento obligatorio",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilaChips {
                EstadoChip(if (t.activo) "Activo" else "Inactivo", if (t.activo) Tono.EXITO else Tono.NEUTRO)
                EstadoChip(obligacionesTexto(t.obligacionesActivas), Tono.INFO)
            }
        }
    }
}

/**
 * Alta ([tipoId] nulo) o edicion de un tipo (solo ADMIN). En la edicion el codigo no se puede cambiar y se guarda la huella
 * recibida: si otro usuario cambia el tipo antes de guardar, el backend responde 409 y se ofrece actualizar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TipoFormScreen(
    vm: FacilityViewModel,
    tipoId: Long?,
    onCancelar: () -> Unit,
    onGuardado: () -> Unit
) {
    val edicion = tipoId != null
    var cargado by rememberSaveable { mutableStateOf(!edicion) }
    var codigo by rememberSaveable { mutableStateOf("") }
    var nombre by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var periodicidad by rememberSaveable { mutableStateOf("") }
    var regla by rememberSaveable { mutableStateOf("DESDE_EJECUCION") }
    var requiereDocumento by rememberSaveable { mutableStateOf(true) }
    var intentado by rememberSaveable { mutableStateOf(false) }
    var dialogoImpacto by rememberSaveable { mutableStateOf(false) }
    var dialogoActivo by rememberSaveable { mutableStateOf(false) }
    val enviando = vm.guardandoTipo
    val errorServidor = vm.errorTipo
    val original = if (edicion) vm.tipoEditado?.takeIf { it.id == tipoId } else null

    fun rellenar(t: TipoGestionDto) {
        codigo = t.codigo; nombre = t.nombre; descripcion = t.descripcion.orEmpty()
        periodicidad = t.periodicidadMeses.toString(); regla = t.reglaRecalculo; requiereDocumento = t.requiereDocumento
        cargado = true
    }

    LaunchedEffect(tipoId) {
        vm.limpiarErrorTipo()
        if (tipoId != null) vm.cargarTipoEditado(tipoId)
    }
    LaunchedEffect(original?.huella) { if (original != null && !cargado) rellenar(original) }

    val codigoNorm = codigo.trim().uppercase()
    val meses = periodicidad.trim().toIntOrNull()
    val errorCodigo = when {
        edicion -> null
        intentado && codigoNorm.isEmpty() -> "Indica el código."
        codigoNorm.isNotEmpty() && !FORMATO_CODIGO_TIPO.matches(codigoNorm) ->
            "Entre 2 y 40 caracteres: letras, números y guiones, sin espacios."
        errorServidor?.campo == CampoTipo.CODIGO -> errorServidor.mensaje
        else -> null
    }
    val errorNombre = when {
        intentado && nombre.isBlank() -> "Indica el nombre."
        nombre.trim().length > 150 -> "Máximo 150 caracteres."
        errorServidor?.campo == CampoTipo.NOMBRE -> errorServidor.mensaje
        else -> null
    }
    val errorPeriodicidad = when {
        intentado && periodicidad.isBlank() -> "Indica la periodicidad en meses."
        periodicidad.isNotBlank() && (meses == null || meses !in MIN_PERIODICIDAD..MAX_PERIODICIDAD) ->
            "Debe ser un número entre $MIN_PERIODICIDAD y $MAX_PERIODICIDAD."
        else -> null
    }
    val errorDescripcion = if (descripcion.trim().length > 2000) "Máximo 2000 caracteres." else null

    // Cambios en campos sensibles (D2): se muestran con sus valores anterior y nuevo antes de confirmar.
    val cambiosSensibles = buildList {
        if (original != null && meses != null && meses != original.periodicidadMeses)
            add("Periodicidad: ${cadaMeses(original.periodicidadMeses).lowercase()} → ${cadaMeses(meses).lowercase()}")
        if (original != null && regla != original.reglaRecalculo)
            add("Regla de cálculo: ${etiquetaRegla(original.reglaRecalculo).lowercase()} → ${etiquetaRegla(regla).lowercase()}")
        if (original != null && requiereDocumento != original.requiereDocumento)
            add("Documento obligatorio: ${if (original.requiereDocumento) "sí" else "no"} → ${if (requiereDocumento) "sí" else "no"}")
    }
    val enUso = original?.obligacionesActivas ?: 0L

    fun enviar(confirmar: Boolean) {
        if (edicion) {
            val o = original ?: return
            vm.editarTipo(tipoId!!, EditarTipoRequest(nombre.trim(), descripcion.trim().ifBlank { null }, meses!!,
                requiereDocumento, regla, o.huella, if (confirmar) true else null)) { onGuardado() }
        } else {
            vm.crearTipo(CrearTipoRequest(codigoNorm, nombre.trim(), descripcion.trim().ifBlank { null }, meses!!,
                requiereDocumento, regla)) { onGuardado() }
        }
    }

    fun guardar() {
        intentado = true
        if (codigoNorm.isEmpty() && !edicion || nombre.isBlank() || meses == null || meses !in MIN_PERIODICIDAD..MAX_PERIODICIDAD ||
            (!edicion && !FORMATO_CODIGO_TIPO.matches(codigoNorm)) || nombre.trim().length > 150 || errorDescripcion != null) return
        if (edicion && cambiosSensibles.isNotEmpty() && enUso > 0) dialogoImpacto = true else enviar(false)
    }

    // Si el backend detecta un uso que la pantalla no conocia, se pide la misma confirmacion.
    LaunchedEffect(errorServidor?.codigo) {
        if (errorServidor?.codigo == "IMPACTO_NO_CONFIRMADO" && cambiosSensibles.isNotEmpty()) dialogoImpacto = true
    }

    ScreenScaffold(
        title = if (edicion) "Editar tipo" else "Nuevo tipo",
        subtitle = "Tipos de mantenimiento",
        onAction = onCancelar,
        scrollable = true,
        barraInferior = {
            Button(onClick = { guardar() }, enabled = !enviando && cargado, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Guardando…")
                } else Text(if (edicion) "Guardar cambios" else "Guardar tipo")
            }
            OutlinedButton(onClick = onCancelar, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
        }
    ) {
        if (edicion) EstadoLectura(vm.lecTipoEditado) { vm.cargarTipoEditado(tipoId!!) }
        if (errorServidor != null && errorServidor.campo == null) {
            if (errorServidor.codigo == "DATOS_DESACTUALIZADOS") {
                Mensaje(errorServidor.mensaje, Tono.ERROR, accion = "Actualizar", enabled = !enviando, onAccion = {
                    vm.limpiarErrorTipo(); cargado = false; vm.cargarTipoEditado(tipoId!!)
                })
            } else if (errorServidor.codigo != "IMPACTO_NO_CONFIRMADO") EstadoError(errorServidor.mensaje)
        }
        if (intentado && listOf(errorCodigo, errorNombre, errorPeriodicidad, errorDescripcion).any { it != null }) {
            Mensaje("Revisa los campos marcados.", Tono.ERROR)
        }
        if (edicion && original != null) {
            FilaChips {
                EstadoChip(if (original.activo) "Activo" else "Inactivo", if (original.activo) Tono.EXITO else Tono.NEUTRO)
                EstadoChip(obligacionesTexto(original.obligacionesActivas), Tono.INFO)
            }
        }

        SectionTitle("Datos del tipo")
        CampoSimple(
            // No se transforma el texto mientras se escribe (choca con la composicion del teclado y desordena lo tecleado):
            // se normaliza a mayusculas al guardar, como hace tambien el backend.
            valor = codigo, onValor = { codigo = it; vm.limpiarErrorTipo() }, etiqueta = "Código",
            error = errorCodigo, enabled = !enviando && !edicion, soloLectura = edicion,
            ayuda = when {
                edicion -> "El código no se puede modificar."
                codigoNorm.isNotEmpty() && codigo.trim() != codigoNorm -> "Se guardará como $codigoNorm."
                else -> "Por ejemplo: PCI-MENS. Letras, números y guiones."
            },
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
        )
        CampoTexto(valor = nombre, onValor = { nombre = it; if (errorServidor?.campo == CampoTipo.NOMBRE) vm.limpiarErrorTipo() },
            etiqueta = "Nombre", error = errorNombre, maximo = 150, enabled = !enviando)
        CampoTexto(valor = descripcion, onValor = { descripcion = it }, etiqueta = "Descripción (opcional)",
            error = errorDescripcion, maximo = 2000, enabled = !enviando, multilinea = true)

        Spacer(Modifier.height(FhEspacio.m))
        SectionTitle("Periodicidad y cálculo")
        CampoSimple(
            valor = periodicidad, onValor = { periodicidad = it.filter(Char::isDigit).take(3) }, etiqueta = "Periodicidad (meses)",
            error = errorPeriodicidad, enabled = !enviando,
            ayuda = "Entre $MIN_PERIODICIDAD y $MAX_PERIODICIDAD meses.",
            teclado = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
        )
        Column(Modifier.selectableGroup()) {
            OpcionRegla("DESDE_EJECUCION", "La próxima fecha se calcula desde el día en que se hizo el mantenimiento.", regla, !enviando) { regla = it }
            OpcionRegla("DESDE_PROGRAMADA", "La próxima fecha se calcula desde la fecha programada, aunque se haga antes o después.", regla, !enviando) { regla = it }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = requiereDocumento, enabled = !enviando, role = Role.Switch,
                    onValueChange = { requiereDocumento = it; vm.limpiarErrorTipo() })
                .then(if (errorServidor?.campo == CampoTipo.DOCUMENTO) Modifier.semantics { error(errorServidor.mensaje) } else Modifier)
        ) {
            Text("Requiere evidencia o certificado", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = requiereDocumento, onCheckedChange = null, enabled = !enviando)
        }
        if (errorServidor?.campo == CampoTipo.DOCUMENTO) {
            Text(errorServidor.mensaje, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (edicion && enUso > 0) {
            Mensaje("Este tipo se usa en ${obligacionesTexto(enUso)}. Si cambias la periodicidad, la regla o el documento, " +
                "se aplicará a partir de ahora: las próximas fechas ya guardadas no cambian.", Tono.INFO)
        }

        if (edicion && original != null) {
            Spacer(Modifier.height(FhEspacio.l))
            SectionTitle("Disponibilidad")
            OutlinedButton(onClick = { dialogoActivo = true }, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (original.activo) "Desactivar tipo" else "Reactivar tipo")
            }
        }
        Spacer(Modifier.height(FhEspacio.l))
    }

    if (dialogoImpacto && original != null) {
        val n = vm.errorTipo?.obligacionesActivas ?: enUso
        AlertDialog(
            onDismissRequest = { dialogoImpacto = false },
            title = { Text("Confirmar cambios") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
                    Text("Este tipo se usa en ${obligacionesTexto(n)}. Cambiará:")
                    cambiosSensibles.forEach { Text("• $it") }
                    Text("Las próximas fechas ya guardadas no se modificarán. Los nuevos valores se usarán en el próximo cálculo.")
                }
            },
            confirmButton = { TextButton(onClick = { dialogoImpacto = false; vm.limpiarErrorTipo(); enviar(true) }) { Text("Confirmar cambios") } },
            dismissButton = { TextButton(onClick = { dialogoImpacto = false; vm.limpiarErrorTipo() }) { Text("Cancelar") } }
        )
    }

    if (dialogoActivo && original != null) {
        val desactivar = original.activo
        AlertDialog(
            onDismissRequest = { dialogoActivo = false },
            title = { Text(if (desactivar) "Desactivar tipo" else "Reactivar tipo") },
            text = {
                Text(if (desactivar) "Este tipo se usa en ${obligacionesTexto(original.obligacionesActivas)}. Dejará de ofrecerse " +
                    "para registrar obligaciones nuevas. Las obligaciones existentes seguirán funcionando y no se borra nada."
                else "El tipo volverá a ofrecerse al registrar obligaciones.")
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogoActivo = false
                    vm.cambiarActivoTipo(original.id, CambiarActivoTipoRequest(!desactivar, original.huella,
                        if (desactivar && original.obligacionesActivas > 0) true else null)) { onGuardado() }
                }) { Text(if (desactivar) "Desactivar" else "Reactivar") }
            },
            dismissButton = { TextButton(onClick = { dialogoActivo = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun OpcionRegla(valor: String, explicacion: String, seleccion: String, enabled: Boolean, onElegir: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = seleccion == valor, enabled = enabled, role = Role.RadioButton, onClick = { onElegir(valor) })
            .padding(vertical = FhEspacio.xs)
    ) {
        RadioButton(selected = seleccion == valor, onClick = null, enabled = enabled)
        Spacer(Modifier.width(FhEspacio.s))
        Column {
            Text(etiquetaRegla(valor), style = MaterialTheme.typography.bodyLarge)
            Text(explicacion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CampoSimple(
    valor: String,
    onValor: (String) -> Unit,
    etiqueta: String,
    error: String?,
    enabled: Boolean,
    ayuda: String?,
    teclado: KeyboardOptions,
    soloLectura: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(etiqueta) },
        isError = error != null,
        enabled = enabled || soloLectura,
        readOnly = soloLectura,
        singleLine = true,
        keyboardOptions = teclado,
        supportingText = (error ?: ayuda)?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)
            .then(if (error != null) Modifier.semantics { error(error) } else Modifier)
    )
}
