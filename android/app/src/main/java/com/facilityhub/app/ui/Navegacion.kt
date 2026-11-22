package com.facilityhub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons

// FH-009 / R3: navegacion por rol. Las pestanas solo deciden que pantallas existentes se muestran en la barra inferior;
// no conceden permisos (cada pantalla sigue pidiendo sus datos al backend, que es la autoridad).

data class Pestana(val ruta: String, val etiqueta: String, val icono: ImageVector)

private val P_PANEL = Pestana("panel", "Panel", FhIcons.Panel)
private val P_CENTROS = Pestana("centros", "Centros", FhIcons.Centros)
private val P_PROVEEDORES = Pestana("proveedores", "Proveedores", FhIcons.Proveedores)
private val P_VALIDACIONES = Pestana("validaciones", "Validaciones", FhIcons.Verificado)
private val P_AVISOS = Pestana("alertas", "Avisos", FhIcons.Avisos)
private val P_TRABAJOS = Pestana("trabajos", "Trabajos", FhIcons.Herramienta)
private val P_MI_LOCAL = Pestana("inquilino-obligaciones", "Mi local", FhIcons.Edificio)
private val P_SOLICITUDES = Pestana("inquilino-solicitudes", "Solicitudes", FhIcons.Solicitud)

/** Pestanas por rol; la primera es la pantalla inicial. Rol ausente o desconocido: ninguna (denegacion por defecto). */
fun pestanasDeRol(rol: String): List<Pestana> = when (rol) {
    "ADMIN" -> listOf(P_PANEL, P_CENTROS, P_PROVEEDORES, P_AVISOS)
    "EXPLOTACION" -> listOf(P_PANEL, P_CENTROS, P_VALIDACIONES, P_AVISOS)
    ROL_PROVEEDOR -> listOf(P_TRABAJOS, P_AVISOS)
    ROL_INQUILINO -> listOf(P_MI_LOCAL, P_SOLICITUDES, P_AVISOS)
    else -> emptyList()
}

/**
 * Presente solo en las pantallas raiz de una pestana: ScreenScaffold oculta la flecha atras y muestra el menu de cuenta.
 * Fuera de una pestana (detalles) vale null y la pantalla muestra «Volver».
 */
val LocalMenuCuenta = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun BarraNavegacion(pestanas: List<Pestana>, rutaActual: String?, noLeidas: Long, onPestana: (Pestana) -> Unit) {
    NavigationBar {
        pestanas.forEach { p ->
            val esAvisos = p.ruta == P_AVISOS.ruta
            val descripcion = if (esAvisos && noLeidas > 0) "Avisos, $noLeidas sin leer" else null
            NavigationBarItem(
                selected = rutaActual == p.ruta,
                onClick = { onPestana(p) },
                icon = {
                    if (esAvisos && noLeidas > 0) {
                        BadgedBox(badge = { Badge { Text(if (noLeidas > 99) "99+" else noLeidas.toString()) } }) {
                            Icon(p.icono, contentDescription = null)
                        }
                    } else {
                        Icon(p.icono, contentDescription = null)
                    }
                },
                // La etiqueta es el texto accesible (y el que localiza la prueba automatica). Con fuente muy grande se
                // recorta con puntos suspensivos; el nombre completo sigue disponible para los lectores de pantalla.
                label = { Text(p.etiqueta, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = true,
                modifier = if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier
            )
        }
    }
}

/** Boton de cuenta (barra superior de las pantallas raiz): usuario, rol y «Cerrar sesión» con confirmacion. */
@Composable
fun MenuCuenta(nombre: String, rol: String, onCerrarSesion: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { abierto = true }) { Icon(FhIcons.Cuenta, contentDescription = "Menú de cuenta") }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            Column(Modifier.padding(horizontal = FhEspacio.l, vertical = FhEspacio.s)) {
                Text(nombre.ifBlank { "Usuario" }, style = MaterialTheme.typography.titleSmall)
                Text(rolLegible(rol), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Cerrar sesión") },
                leadingIcon = { Icon(FhIcons.Salir, contentDescription = null) },
                onClick = { abierto = false; confirmar = true }
            )
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tendrás que volver a introducir tu correo y tu contraseña para entrar.") },
            confirmButton = { TextButton(onClick = { confirmar = false; onCerrarSesion() }) { Text("Cerrar sesión") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}
