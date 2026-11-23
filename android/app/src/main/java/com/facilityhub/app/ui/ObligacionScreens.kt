package com.facilityhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.CrearObligacionRequest
import com.facilityhub.app.data.ObligacionCentroDto
import com.facilityhub.app.ui.theme.FhEspacio
import java.time.LocalDate

// FH-010B / RF-09: alta de obligaciones de mantenimiento (solo gestion) y su consulta en zona comun.

internal const val MAX_OBSERVACIONES_OBLIGACION = 2000

/** Confirmacion de alta de obligacion: una vez, en la lista de destino; se descarta al salir de ella. */
@Composable
internal fun AvisoObligacion(vm: FacilityViewModel) {
    val aviso = vm.avisoObligacion ?: return
    Mensaje(aviso, Tono.EXITO, accion = "Cerrar", onAccion = { vm.consumirAvisoObligacion() })
    DisposableEffect(aviso) { onDispose { vm.consumirAvisoObligacion() } }
}

/** Fila de una obligacion de zona comun (en «Instalaciones» del centro). */
@Composable
internal fun FilaObligacionCentro(o: ObligacionCentroDto) {
    Column(Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs)) {
        Text(o.tipoMantenimiento, style = MaterialTheme.typography.bodyLarge)
        Text(
            listOfNotNull(o.instalacion, "Cada ${o.periodicidadMeses} meses", "Próxima: ${fechaCorta(o.proximaFecha)}",
                etiquetaResponsable(o.responsable)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun etiquetaResponsable(r: String?): String = when (r) {
    "INQUILINO" -> "Responsable: inquilino"
    "NHOOD" -> "Responsable: NHOOD"
    else -> ""
}

/**
 * Formulario «Nueva obligación». El centro lo fija la navegacion; la ubicacion se preselecciona con [localInicial]
 * (null = zona comun). Crea SOLO la obligacion: el primer mantenimiento se programa despues (RF-10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObligacionFormScreen(
    vm: FacilityViewModel,
    centroId: Long,
    centroNombre: String,
    localInicial: Long?,
    onCancelar: () -> Unit,
    onCreada: (ObligacionCentroDto) -> Unit
) {
    var localId by rememberSaveable { mutableStateOf(localInicial) }
    var instalacionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var tipoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var responsable by rememberSaveable { mutableStateOf("NHOOD") }
    var fechaInicio by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var proximaFecha by rememberSaveable { mutableStateOf("") }
    var observaciones by rememberSaveable { mutableStateOf("") }
    var intentado by rememberSaveable { mutableStateOf(false) }
    val enviando = vm.guardandoObligacion
    val errorServidor = vm.errorObligacion

    LaunchedEffect(centroId) {
        vm.limpiarErrorObligacion()
        vm.cargarLocales(centroId)
        vm.cargarInstalacionesCentro(centroId)
        vm.cargarTipos()
    }

    val zonaComun = localId == null
    val localesCentro = if (vm.lecLocales.clave == centroId) vm.locales else emptyList()
    val instalacionesUbicacion = if (vm.lecInstalacionesCentro.clave == centroId)
        vm.instalacionesCentro.filter { it.localId == localId } else emptyList()
    val tipo = vm.tiposMantenimiento.firstOrNull { it.id == tipoId }
    val inicio = runCatching { LocalDate.parse(fechaInicio) }.getOrNull()
    val proxima = runCatching { LocalDate.parse(proximaFecha) }.getOrNull()
    val calculada = if (inicio != null && tipo != null) inicio.plusMonths(tipo.periodicidadMeses.toLong()) else null

    val errorTipo = when {
        intentado && tipo == null -> "Elige el tipo de mantenimiento."
        errorServidor?.campo == CampoObligacion.TIPO -> errorServidor.mensaje
        else -> null
    }
    val errorResponsable = when {
        responsable == "INQUILINO" && zonaComun -> "En zona común el responsable solo puede ser NHOOD."
        errorServidor?.campo == CampoObligacion.RESPONSABLE -> errorServidor.mensaje
        else -> null
    }
    val errorInicio = if (intentado && inicio == null) "Indica la fecha de inicio." else null
    val errorProxima = when {
        inicio != null && proxima != null && proxima.isBefore(inicio) -> "No puede ser anterior a la fecha de inicio."
        errorServidor?.campo == CampoObligacion.FECHAS -> errorServidor.mensaje
        else -> null
    }
    val errorObservaciones = if (observaciones.trim().length > MAX_OBSERVACIONES_OBLIGACION)
        "Máximo $MAX_OBSERVACIONES_OBLIGACION caracteres." else null

    fun guardar() {
        intentado = true
        val locales = tipo == null || inicio == null || (responsable == "INQUILINO" && zonaComun) ||
            (proxima != null && proxima.isBefore(inicio)) || errorObservaciones != null
        if (locales) return
        vm.registrarObligacion(
            centroId,
            CrearObligacionRequest(localId, instalacionId, tipo!!.id, responsable, fechaInicio,
                proximaFecha.ifBlank { null }, observaciones.trim().ifBlank { null }),
            onCreada
        )
    }

    ScreenScaffold(
        title = "Nueva obligación",
        subtitle = centroNombre,
        onAction = onCancelar,
        scrollable = true,
        barraInferior = {
            Button(onClick = { guardar() }, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Guardando…")
                } else {
                    Text("Guardar obligación")
                }
            }
            OutlinedButton(onClick = onCancelar, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
        }
    ) {
        EstadoLectura(vm.lecTipos) { vm.cargarTipos(forzar = true) }
        if (errorServidor != null && errorServidor.campo == null) EstadoError(errorServidor.mensaje)
        if (intentado && listOf(errorTipo, errorResponsable, errorInicio, errorProxima, errorObservaciones).any { it != null }) {
            Mensaje("Revisa los campos marcados.", Tono.ERROR)
        }
        Mensaje(
            "Se registra solo la obligación periódica. El primer mantenimiento se programa después, desde la obligación.",
            Tono.INFO
        )

        SectionTitle("Ubicación")
        DatoEtiquetado("Centro", centroNombre)
        SelectorOpciones(
            etiqueta = "Zona común o local",
            seleccion = localId,
            opciones = listOf<Pair<Long?, String>>(null to "Zona común") +
                localesCentro.map { it.id to listOfNotNull(it.codigo, it.nombre).joinToString(" · ") },
            textoSinCargar = if (localId == null) "Zona común" else "Local seleccionado",
            enabled = !enviando,
            onElegir = { nuevo ->
                localId = nuevo; instalacionId = null
                if (nuevo == null) responsable = "NHOOD"
                vm.limpiarErrorObligacion()
            }
        )
        SelectorOpciones(
            etiqueta = "Instalación (opcional)",
            seleccion = instalacionId,
            opciones = listOf<Pair<Long?, String>>(null to "Sin instalación concreta") +
                instalacionesUbicacion.map { it.id to "${it.nombre} · ${it.tipo}" },
            textoSinCargar = "Sin instalación concreta",
            enabled = !enviando,
            ayuda = if (zonaComun) "Solo instalaciones de la zona común." else "Solo instalaciones de este local.",
            onElegir = { instalacionId = it; vm.limpiarErrorObligacion() }
        )

        Spacer(Modifier.height(FhEspacio.l))
        SectionTitle("Mantenimiento exigible")
        SelectorOpciones(
            etiqueta = "Tipo de mantenimiento",
            seleccion = tipoId,
            opciones = vm.tiposMantenimiento.map { it.id to it.nombre },
            textoSinCargar = "",
            enabled = !enviando && vm.tiposMantenimiento.isNotEmpty(),
            error = errorTipo,
            ayuda = tipo?.let { "Periodicidad: cada ${it.periodicidadMeses} ${if (it.periodicidadMeses == 1) "mes" else "meses"}." }
                ?: "La periodicidad la fija el tipo elegido.",
            onElegir = { tipoId = it; vm.limpiarErrorObligacion() }
        )

        Spacer(Modifier.height(FhEspacio.l))
        SectionTitle("Responsable")
        Column(Modifier.selectableGroup().then(if (errorResponsable != null) Modifier.semantics { error(errorResponsable) } else Modifier)) {
            OpcionRadio("NHOOD (propiedad)", responsable == "NHOOD", enabled = !enviando) {
                responsable = "NHOOD"; vm.limpiarErrorObligacion()
            }
            OpcionRadio("Inquilino del local", responsable == "INQUILINO", enabled = !enviando && !zonaComun) {
                responsable = "INQUILINO"; vm.limpiarErrorObligacion()
            }
        }
        Text(
            errorResponsable ?: if (zonaComun) "En zona común el responsable es siempre NHOOD."
                else "El inquilino solo puede ser responsable si el local tiene una ocupación vigente.",
            style = MaterialTheme.typography.bodySmall,
            color = if (errorResponsable != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(FhEspacio.l))
        SectionTitle("Fechas")
        CampoFecha(
            valor = fechaInicio,
            onValor = { fechaInicio = it; vm.limpiarErrorObligacion() },
            etiqueta = "Fecha de inicio",
            enabled = !enviando,
            soporte = errorInicio
        )
        CampoFecha(
            valor = proximaFecha,
            onValor = { proximaFecha = it; vm.limpiarErrorObligacion() },
            etiqueta = "Próxima fecha (opcional)",
            enabled = !enviando,
            opcional = true,
            soporte = errorProxima ?: when {
                proxima != null -> "Fecha indicada manualmente."
                calculada != null -> "Si la dejas vacía se calcula: inicio + ${tipo!!.periodicidadMeses} meses = ${fechaCorta(calculada.toString())}."
                else -> "Si la dejas vacía se calcula a partir de la fecha de inicio y la periodicidad del tipo."
            }
        )

        Spacer(Modifier.height(FhEspacio.m))
        CampoTexto(
            valor = observaciones, onValor = { observaciones = it }, etiqueta = "Observaciones (opcional)",
            error = errorObservaciones, maximo = MAX_OBSERVACIONES_OBLIGACION, enabled = !enviando, multilinea = true
        )
        Spacer(Modifier.height(FhEspacio.l))
    }
}

@Composable
private fun OpcionRadio(texto: String, seleccionada: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = seleccionada, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    ) {
        RadioButton(selected = seleccionada, onClick = null, enabled = enabled)
        Spacer(Modifier.width(FhEspacio.s))
        Text(texto, style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

/** Lista desplegable de solo lectura (Material 3) con error y ayuda bajo el campo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectorOpciones(
    etiqueta: String,
    seleccion: Long?,
    opciones: List<Pair<Long?, String>>,
    textoSinCargar: String,
    enabled: Boolean,
    error: String? = null,
    ayuda: String? = null,
    onElegir: (Long?) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    val texto = opciones.firstOrNull { it.first == seleccion }?.second ?: textoSinCargar
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { if (enabled) abierto = it }) {
        OutlinedTextField(
            value = texto,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(etiqueta) },
            isError = error != null,
            supportingText = (error ?: ayuda)?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FhEspacio.xs)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .then(if (error != null) Modifier.semantics { error(error) } else Modifier)
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (valor, nombre) ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = { onElegir(valor); abierto = false })
            }
        }
    }
}
