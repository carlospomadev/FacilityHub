package com.facilityhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.AlertaDto

// FH-006 / P6-D1: icono de avisos con contador y bandeja «Mis notificaciones».

internal fun etiquetaAlerta(tipo: String) = when (tipo) {
    "ASIGNACION" -> "Nuevo trabajo asignado"
    "PROVEEDOR_DECLARADO" -> "Proveedor declarado"
    "EVIDENCIAS_PRESENTADAS" -> "Intervención para validar"
    "CORRECCION_SOLICITADA" -> "Corrección solicitada"
    "APROBADO" -> "Mantenimiento aprobado"
    // FH-006 / P6-D2: recordatorios de vencimiento.
    "VENCIMIENTO_PROXIMO" -> "Próximo a vencer"
    "MANTENIMIENTO_VENCIDO" -> "Mantenimiento vencido"
    "VALIDACION_PENDIENTE_VENCIDA" -> "Validación pendiente (vencido)"
    else -> "Aviso"
}

private val TIPOS_VENCIMIENTO = setOf("VENCIMIENTO_PROXIMO", "MANTENIMIENTO_VENCIDO")

// Destinos compatibles por rol. Una alerta NO concede acceso: la pantalla de destino vuelve a pedir el recurso al
// backend, que comprueba permisos (403/404 se muestran como falta de acceso).
sealed interface DestinoAlerta {
    data class Trabajo(val mantenimientoId: Long) : DestinoAlerta
    data class Intervencion(val intervencionId: Long) : DestinoAlerta
    data object Declaraciones : DestinoAlerta
    data class MantenimientosInquilino(val obligacionId: Long) : DestinoAlerta
    data class MantenimientoGestion(val mantenimientoId: Long) : DestinoAlerta
}

internal fun destinoAlerta(a: AlertaDto, rol: String): DestinoAlerta? = when {
    rol == "PROVEEDOR" && a.tipo == "ASIGNACION" && a.mantenimientoId != null -> DestinoAlerta.Trabajo(a.mantenimientoId)
    rol == "PROVEEDOR" && a.tipo in setOf("CORRECCION_SOLICITADA", "APROBADO") && a.intervencionId != null ->
        DestinoAlerta.Intervencion(a.intervencionId)
    rol in ROLES_GESTION && a.tipo == "EVIDENCIAS_PRESENTADAS" && a.intervencionId != null -> DestinoAlerta.Intervencion(a.intervencionId)
    rol in ROLES_GESTION && a.tipo == "PROVEEDOR_DECLARADO" -> DestinoAlerta.Declaraciones
    rol == ROL_INQUILINO && a.tipo == "APROBADO" && a.obligacionId != null -> DestinoAlerta.MantenimientosInquilino(a.obligacionId)
    // P6-D2: recordatorios de vencimiento.
    rol == "PROVEEDOR" && a.tipo in TIPOS_VENCIMIENTO && a.mantenimientoId != null -> DestinoAlerta.Trabajo(a.mantenimientoId)
    rol in ROLES_GESTION && a.tipo in TIPOS_VENCIMIENTO && a.mantenimientoId != null ->
        DestinoAlerta.MantenimientoGestion(a.mantenimientoId)
    rol in ROLES_GESTION && a.tipo == "VALIDACION_PENDIENTE_VENCIDA" && a.intervencionId != null ->
        DestinoAlerta.Intervencion(a.intervencionId)
    rol == ROL_INQUILINO && (a.tipo in TIPOS_VENCIMIENTO || a.tipo == "VALIDACION_PENDIENTE_VENCIDA") && a.obligacionId != null ->
        DestinoAlerta.MantenimientosInquilino(a.obligacionId)
    else -> null
}

// Acceso a la bandeja con el numero de no leidas. Solo se muestra en pantallas raiz (con sesion iniciada); el contador
// se pide al mostrarse la pantalla y al volver a ella.
@Composable
fun BotonAlertas(alertas: AlertasViewModel, onAbrir: () -> Unit) {
    LaunchedEffect(Unit) { alertas.cargarContador() }
    TextButton(onClick = onAbrir) {
        Text("Avisos")
        if (alertas.noLeidas > 0) {
            Spacer(Modifier.width(4.dp))
            Badge { Text(if (alertas.noLeidas > 99) "99+" else alertas.noLeidas.toString()) }
        }
    }
}

@Composable
fun MisNotificacionesScreen(
    alertas: AlertasViewModel,
    rol: String,
    onAbrir: (DestinoAlerta) -> Unit,
    onSesionCaducada: () -> Unit,
    onBack: () -> Unit
) {
    // Se recarga al abrir y al volver desde otra pantalla (el destino vuelve a entrar en composicion).
    LaunchedEffect(Unit) { alertas.clearError(); alertas.cargar() }
    LaunchedEffect(alertas.sesionCaducada) { if (alertas.sesionCaducada) onSesionCaducada() }

    ScreenScaffold(title = "Mis notificaciones", subtitle = "${alertas.noLeidas} sin leer", onAction = onBack, scrollable = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = alertas.soloNoLeidas, onCheckedChange = { alertas.cambiarFiltro(it) }, enabled = !alertas.loading)
            Text("Solo no leídas")
        }
        if (alertas.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        alertas.error?.let { e ->
            Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
            TextButton(onClick = { alertas.cargar() }, enabled = !alertas.loading) { Text("Reintentar") }
        }
        if (alertas.cargada && !alertas.loading && alertas.error == null && alertas.alertas.isEmpty()) {
            Text(if (alertas.soloNoLeidas) "No tienes avisos sin leer." else "No tienes avisos.", modifier = Modifier.padding(vertical = 12.dp))
        }
        alertas.alertas.forEach { a ->
            val leida = a.leidaAt != null
            val destino = destinoAlerta(a, rol)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(etiquetaAlerta(a.tipo), fontWeight = if (leida) FontWeight.Normal else FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (leida) "Leída" else "Nueva", style = MaterialTheme.typography.labelSmall,
                            color = if (leida) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                    }
                    Text(a.mensaje)
                    Text(fechaHora(a.createdAt), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (destino != null) TextButton(onClick = { alertas.marcarLeida(a) { onAbrir(destino) } }, enabled = !alertas.loading) {
                            Text("Abrir")
                        }
                        if (!leida) TextButton(onClick = { alertas.marcarLeida(a) }, enabled = !alertas.loading) { Text("Marcar como leída") }
                    }
                }
            }
        }
    }
}
