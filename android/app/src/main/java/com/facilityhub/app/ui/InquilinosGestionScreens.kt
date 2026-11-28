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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.CambiarActivoInquilinoRequest
import com.facilityhub.app.data.CrearInquilinoRequest
import com.facilityhub.app.data.EditarInquilinoRequest
import com.facilityhub.app.data.InquilinoGestionDto
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-015 / RF-05: gestion de inquilinos (activos e inactivos). ADMIN: alta, edicion, desactivacion y reactivacion.
// EXPLOTACION: consulta. La vinculacion de inquilinos a locales (ocupaciones) y sus usuarios quedan fuera (D9).

internal const val MIN_CIF_INQUILINO = 5
internal const val MAX_CIF_INQUILINO = 20
internal const val MAX_RAZON_SOCIAL = 150
internal const val MAX_NOMBRE_COMERCIAL = 120
internal const val MAX_EMAIL_INQUILINO = 150
internal const val MAX_TELEFONO_INQUILINO = 30

/** Misma normalizacion que el backend (D2): sin espacios, guiones ni puntos y en mayusculas. */
internal fun normalizarCif(cif: String) = cif.replace(Regex("[\\s.\\-]"), "").uppercase()
internal fun cifValido(cif: String) = cif.length in MIN_CIF_INQUILINO..MAX_CIF_INQUILINO && cif.matches(Regex("^[A-Z0-9]+$"))
private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+$")
private val TELEFONO = Regex("^[0-9+()\\- ]*$")

private fun cuentaInq(n: Long, singular: String, plural: String) = "$n ${if (n == 1L) singular else plural}"

@Composable
internal fun AvisoInquilino(vm: FacilityViewModel) {
    val aviso = vm.avisoInquilino ?: return
    Mensaje(aviso, Tono.EXITO, accion = "Cerrar", onAccion = { vm.consumirAvisoInquilino() })
    DisposableEffect(aviso) { onDispose { vm.consumirAvisoInquilino() } }
}

/** Todos los inquilinos (activos e inactivos) con su uso y un buscador local. Para EXPLOTACION es solo consulta. */
@Composable
fun InquilinosGestionScreen(
    vm: FacilityViewModel,
    onNuevo: () -> Unit,
    onInquilino: (InquilinoGestionDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { vm.cargarCatalogoInquilinos() }
    var verInactivos by rememberSaveable { mutableStateOf(true) }
    var buscar by rememberSaveable { mutableStateOf("") }
    val texto = buscar.trim().lowercase()
    val cifBuscado = normalizarCif(buscar)
    val lista = vm.catalogoInquilinos.filter { i ->
        (verInactivos || i.activo) && (texto.isEmpty() ||
            i.razonSocial.lowercase().contains(texto) || i.nombreComercial.orEmpty().lowercase().contains(texto) ||
            (cifBuscado.isNotEmpty() && i.cif.orEmpty().uppercase().contains(cifBuscado)))
    }

    PantallaLista(title = "Gestionar inquilinos", subtitle = if (vm.esAdmin) "Inquilinos" else "Inquilinos (consulta)",
        onAction = onBack) {
        item {
            AvisoInquilino(vm)
            EstadoLectura(vm.lecCatalogoInquilinos) { vm.cargarCatalogoInquilinos(forzar = true) }
            if (vm.esAdmin) {
                Button(onClick = onNuevo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(FhIcons.Anadir, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Nuevo inquilino")
                }
            } else {
                Mensaje("Solo un administrador puede registrar, modificar o desactivar inquilinos.", Tono.INFO)
            }
            Mensaje("La asignación de inquilinos a locales todavía no se hace desde la app.", Tono.INFO)
            OutlinedTextField(value = buscar, onValueChange = { buscar = it }, label = { Text("Buscar por CIF o nombre") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = verInactivos, role = Role.Switch, onValueChange = { verInactivos = it })
            ) {
                Text("Mostrar inquilinos inactivos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = verInactivos, onCheckedChange = null)
            }
            if (vm.lecCatalogoInquilinos.vacia(lista)) EstadoVacio("No hay inquilinos.")
        }
        items(lista, key = { it.id }) { i ->
            Box(Modifier.padding(vertical = FhEspacio.xs)) {
                TarjetaInquilinoGestion(i, if (vm.esAdmin) ({ onInquilino(i) }) else null)
            }
        }
    }
}

@Composable
private fun TarjetaInquilinoGestion(i: InquilinoGestionDto, onClick: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(FhEspacio.l)) {
            Text(i.nombreComercial ?: i.razonSocial, style = MaterialTheme.typography.titleMedium)
            // Registros antiguos pueden no tener CIF (D3).
            Text(listOfNotNull(i.cif ?: "Sin CIF", i.razonSocial.takeIf { i.nombreComercial != null }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilaChips {
                EstadoChip(if (i.activo) "Activo" else "Inactivo", if (i.activo) Tono.EXITO else Tono.NEUTRO)
                if (i.ocupacionesVigentes > 0)
                    EstadoChip(cuentaInq(i.ocupacionesVigentes, "local ocupado", "locales ocupados"), Tono.INFO)
                if (i.ocupacionesFuturas > 0)
                    EstadoChip(cuentaInq(i.ocupacionesFuturas, "ocupación futura", "ocupaciones futuras"), Tono.INFO)
                if (i.mantenimientosAbiertos > 0)
                    EstadoChip(cuentaInq(i.mantenimientosAbiertos, "mantenimiento abierto", "mantenimientos abiertos"), Tono.AVISO)
            }
        }
    }
}

/**
 * Alta ([inquilinoId] nulo) o edicion de un inquilino (solo ADMIN). El CIF no se puede cambiar (solo se asigna a un
 * registro antiguo que no lo tenga) y se guarda la huella recibida: si otro usuario cambia el inquilino antes de guardar,
 * el backend responde 409 y se ofrece actualizar (D7).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InquilinoFormScreen(
    vm: FacilityViewModel,
    inquilinoId: Long?,
    onCancelar: () -> Unit,
    onGuardado: () -> Unit
) {
    val edicion = inquilinoId != null
    var cargado by rememberSaveable { mutableStateOf(!edicion) }
    var cif by rememberSaveable { mutableStateOf("") }
    var razonSocial by rememberSaveable { mutableStateOf("") }
    var nombreComercial by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var intentado by rememberSaveable { mutableStateOf(false) }
    var dialogoActivo by rememberSaveable { mutableStateOf(false) }
    val enviando = vm.guardandoInquilino
    val errorServidor = vm.errorInquilino
    val original = if (edicion) vm.inquilinoEditado?.takeIf { it.id == inquilinoId } else null
    // El CIF solo se escribe en el alta o en un registro antiguo que no lo tiene (D4).
    val cifEditable = !edicion || (original != null && original.cif == null)

    fun rellenar(i: InquilinoGestionDto) {
        cif = i.cif.orEmpty(); razonSocial = i.razonSocial; nombreComercial = i.nombreComercial.orEmpty()
        email = i.emailContacto.orEmpty(); telefono = i.telefono.orEmpty()
        cargado = true
    }

    LaunchedEffect(inquilinoId) {
        vm.limpiarErrorInquilino()
        if (inquilinoId != null) vm.cargarInquilinoEditado(inquilinoId)
    }
    LaunchedEffect(original?.huella) { if (original != null && !cargado) rellenar(original) }

    val cifNorm = normalizarCif(cif)
    val errorCif = when {
        !cifEditable -> null
        // En un registro antiguo el CIF sigue siendo opcional: solo se valida si se escribe.
        intentado && cifNorm.isEmpty() && !edicion -> "Indica el CIF/NIF."
        cifNorm.isNotEmpty() && !cifValido(cifNorm) -> "De $MIN_CIF_INQUILINO a $MAX_CIF_INQUILINO letras o dígitos."
        errorServidor?.campo == CampoInquilino.CIF -> errorServidor.mensaje
        else -> null
    }
    val errorRazon = when {
        intentado && razonSocial.isBlank() -> "Indica la razón social."
        razonSocial.trim().length > MAX_RAZON_SOCIAL -> "Máximo $MAX_RAZON_SOCIAL caracteres."
        else -> null
    }
    val errorNombre = if (nombreComercial.trim().length > MAX_NOMBRE_COMERCIAL) "Máximo $MAX_NOMBRE_COMERCIAL caracteres." else null
    val errorEmail = when {
        email.trim().length > MAX_EMAIL_INQUILINO -> "Máximo $MAX_EMAIL_INQUILINO caracteres."
        email.isNotBlank() && !EMAIL.matches(email.trim()) -> "Escribe un email válido."
        else -> null
    }
    val errorTelefono = when {
        telefono.trim().length > MAX_TELEFONO_INQUILINO -> "Máximo $MAX_TELEFONO_INQUILINO caracteres."
        !TELEFONO.matches(telefono.trim()) -> "Solo dígitos, espacios y los signos + - ( )."
        else -> null
    }

    fun guardar() {
        intentado = true
        val cifMal = cifEditable && ((!edicion && cifNorm.isEmpty()) || (cifNorm.isNotEmpty() && !cifValido(cifNorm)))
        if (cifMal || razonSocial.isBlank() || razonSocial.trim().length > MAX_RAZON_SOCIAL || errorNombre != null ||
            errorEmail != null || errorTelefono != null) return
        if (edicion) {
            val o = original ?: return
            vm.editarInquilino(inquilinoId!!, EditarInquilinoRequest(
                if (o.cif == null) cifNorm.ifEmpty { null } else null, razonSocial.trim(), nombreComercial.trim().ifBlank { null },
                email.trim().ifBlank { null }, telefono.trim().ifBlank { null }, o.huella)) { onGuardado() }
        } else {
            vm.crearInquilino(CrearInquilinoRequest(cifNorm, razonSocial.trim(), nombreComercial.trim().ifBlank { null },
                email.trim().ifBlank { null }, telefono.trim().ifBlank { null })) { onGuardado() }
        }
    }

    ScreenScaffold(
        title = if (edicion) "Editar inquilino" else "Nuevo inquilino",
        subtitle = "Gestionar inquilinos",
        onAction = onCancelar,
        scrollable = true,
        barraInferior = {
            Button(onClick = { guardar() }, enabled = !enviando && cargado, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(FhEspacio.s))
                    Text("Guardando…")
                } else Text(if (edicion) "Guardar cambios" else "Guardar inquilino")
            }
            OutlinedButton(onClick = onCancelar, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
        }
    ) {
        if (edicion) EstadoLectura(vm.lecInquilinoEditado) { vm.cargarInquilinoEditado(inquilinoId!!) }
        if (errorServidor != null && errorServidor.campo == null) {
            if (errorServidor.codigo == "DATOS_DESACTUALIZADOS") {
                Mensaje(errorServidor.mensaje, Tono.ERROR, accion = "Actualizar", enabled = !enviando, onAccion = {
                    vm.limpiarErrorInquilino(); cargado = false; vm.cargarInquilinoEditado(inquilinoId!!)
                })
            } else EstadoError(errorServidor.mensaje)
        }
        if (intentado && listOf(errorCif, errorRazon, errorNombre, errorEmail, errorTelefono).any { it != null }) {
            Mensaje("Revisa los campos marcados.", Tono.ERROR)
        }
        if (edicion && original != null) {
            FilaChips {
                EstadoChip(if (original.activo) "Activo" else "Inactivo", if (original.activo) Tono.EXITO else Tono.NEUTRO)
                EstadoChip(cuentaInq(original.ocupaciones, "ocupación registrada", "ocupaciones registradas"), Tono.INFO)
                EstadoChip(cuentaInq(original.usuarios, "usuario", "usuarios"), Tono.INFO)
            }
        }

        SectionTitle("Datos del inquilino")
        CampoSimple(
            // No se transforma el texto mientras se escribe (choca con la composicion del teclado): se normaliza al guardar.
            valor = cif, onValor = { cif = it; vm.limpiarErrorInquilino() }, etiqueta = "CIF/NIF",
            error = errorCif, enabled = !enviando && cifEditable, soloLectura = !cifEditable,
            ayuda = when {
                !cifEditable -> "El CIF/NIF no se puede modificar."
                edicion -> "Este inquilino no tiene CIF/NIF: puedes asignarlo una sola vez."
                cifNorm.isNotEmpty() && cif.trim() != cifNorm -> "Se guardará como $cifNorm."
                else -> "Único entre todos los inquilinos."
            },
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
        )
        CampoTexto(valor = razonSocial, onValor = { razonSocial = it }, etiqueta = "Razón social", error = errorRazon,
            maximo = MAX_RAZON_SOCIAL, enabled = !enviando)
        CampoTexto(valor = nombreComercial, onValor = { nombreComercial = it }, etiqueta = "Nombre comercial (opcional)",
            error = errorNombre, maximo = MAX_NOMBRE_COMERCIAL, enabled = !enviando)
        CampoSimple(valor = email, onValor = { email = it }, etiqueta = "Email de contacto (opcional)", error = errorEmail,
            enabled = !enviando, ayuda = null,
            teclado = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        CampoSimple(valor = telefono, onValor = { telefono = it }, etiqueta = "Teléfono (opcional)", error = errorTelefono,
            enabled = !enviando, ayuda = null,
            teclado = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done))

        if (edicion && original != null) {
            Spacer(Modifier.height(FhEspacio.l))
            SectionTitle("Disponibilidad")
            if (!original.activo) {
                Mensaje("Inquilino inactivo: sus usuarios no acceden al área del inquilino. Su histórico se conserva.", Tono.INFO)
            }
            OutlinedButton(onClick = { vm.limpiarErrorInquilino(); dialogoActivo = true }, enabled = !enviando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (original.activo) "Desactivar inquilino" else "Reactivar inquilino")
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
            title = { Text(if (!desactivar) "Reactivar inquilino" else if (bloqueado) "No se puede desactivar" else "Desactivar inquilino") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(FhEspacio.s)) {
                    when {
                        !desactivar -> Text("Sus usuarios volverán a acceder al área del inquilino si tiene una ocupación vigente.")
                        bloqueado -> {
                            Text("El inquilino está en uso:")
                            Text("• ${cuentaInq(original.ocupacionesVigentes, "ocupación vigente", "ocupaciones vigentes")}")
                            if (original.ocupacionesFuturas > 0)
                                Text("• ${cuentaInq(original.ocupacionesFuturas, "ocupación futura", "ocupaciones futuras")} (todavía sin empezar)")
                            Text("• ${cuentaInq(original.mantenimientosAbiertos, "mantenimiento abierto", "mantenimientos abiertos")} a su cargo")
                            Text("Deben terminar o cerrarse antes de desactivarlo.")
                        }
                        else -> {
                            Text("Resumen del impacto:")
                            Text("• ${cuentaInq(original.ocupaciones, "ocupación en el histórico", "ocupaciones en el histórico")}")
                            Text("• ${cuentaInq(original.usuarios, "usuario vinculado", "usuarios vinculados")}")
                            Text("• ${cuentaInq(original.solicitudesPendientes, "solicitud de proveedor pendiente", "solicitudes de proveedor pendientes")}")
                            Text("• ${cuentaInq(original.declaracionesActivas, "proveedor declarado", "proveedores declarados")}")
                            Text("Sus usuarios dejarán de acceder al área del inquilino. No se borra nada: el histórico de " +
                                "ocupación, los usuarios, las solicitudes y los documentos se conservan y podrás reactivarlo.")
                        }
                    }
                }
            },
            confirmButton = {
                if (!bloqueado) {
                    TextButton(onClick = {
                        dialogoActivo = false
                        vm.cambiarActivoInquilino(original.id, CambiarActivoInquilinoRequest(!desactivar, original.huella,
                            if (desactivar) true else null)) { onGuardado() }
                    }) { Text(if (desactivar) "Desactivar" else "Reactivar") }
                }
            },
            dismissButton = { TextButton(onClick = { dialogoActivo = false }) { Text(if (bloqueado) "Cerrar" else "Cancelar") } }
        )
    }
}
