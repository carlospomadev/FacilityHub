package com.facilityhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.facilityhub.app.ui.theme.FhEspacio
import com.facilityhub.app.ui.theme.FhIcons
import com.facilityhub.app.ui.theme.FhTema

// FH-009 / R1: componentes comunes (scaffold, estados, chips). Solo presentacion: no cambian navegacion, permisos ni
// llamadas al backend. Los textos que usa la prueba automatica ("Volver", "Salir", etiquetas de estado) se conservan.

/** Tono semantico de un estado. */
enum class Tono { NEUTRO, PRIMARIO, INFO, EXITO, AVISO, ERROR, HISTORICO }

/** Colores de contenedor y contenido de un tono. */
@Composable
internal fun coloresTono(tono: Tono): Pair<Color, Color> {
    val s = FhTema.semanticos
    val c = MaterialTheme.colorScheme
    return when (tono) {
        Tono.NEUTRO -> c.surfaceContainerHighest to c.onSurfaceVariant
        Tono.PRIMARIO -> c.primaryContainer to c.onPrimaryContainer
        Tono.INFO -> s.infoContenedor to s.onInfoContenedor
        Tono.EXITO -> s.exitoContenedor to s.onExitoContenedor
        Tono.AVISO -> s.avisoContenedor to s.onAvisoContenedor
        Tono.ERROR -> c.errorContainer to c.onErrorContainer
        Tono.HISTORICO -> s.historicoContenedor to s.onHistoricoContenedor
    }
}

// ---- Correspondencia estado -> tono e icono (una sola fuente para todas las pantallas) ----

internal fun tonoValidacion(e: String?): Tono = when (e) {
    "PENDIENTE_VALIDACION" -> Tono.INFO
    "REQUIERE_CORRECCION" -> Tono.ERROR
    "APROBADO" -> Tono.EXITO
    "CERRADO_SIN_CUMPLIMIENTO" -> Tono.AVISO
    else -> Tono.NEUTRO
}

internal fun iconoValidacion(e: String?): ImageVector? = when (e) {
    "PENDIENTE_VALIDACION" -> FhIcons.Reloj
    "REQUIERE_CORRECCION" -> FhIcons.Editar
    "APROBADO" -> FhIcons.Verificado
    "CERRADO_SIN_CUMPLIMIENTO" -> FhIcons.Informacion
    else -> null
}

// FINALIZADO en P6 significa "ejecutado y enviado" (pendiente de validar), no cumplido: tono informativo, no de exito.
internal fun tonoEstado(e: String?): Tono = when (e) {
    "EN_PROCESO" -> Tono.PRIMARIO
    "FINALIZADO" -> Tono.INFO
    "CANCELADO" -> Tono.NEUTRO
    else -> Tono.NEUTRO
}

internal fun tonoRegistroHistorial(tipo: String?): Tono = when (tipo) {
    "APROBADO" -> Tono.EXITO
    "CERRADO_SIN_CUMPLIMIENTO" -> Tono.AVISO
    else -> Tono.HISTORICO // HISTORICO_P5: nunca se presenta como aprobado
}

internal fun iconoRegistroHistorial(tipo: String?): ImageVector = when (tipo) {
    "APROBADO" -> FhIcons.Verificado
    "CERRADO_SIN_CUMPLIMIENTO" -> FhIcons.Informacion
    else -> FhIcons.Historico
}

/** Chip de estado no interactivo: texto legible, icono opcional y tono semantico. */
@Composable
internal fun EstadoChip(texto: String, tono: Tono, icono: ImageVector? = null, modifier: Modifier = Modifier) {
    val (fondo, contenido) = coloresTono(tono)
    Surface(color = fondo, contentColor = contenido, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = FhEspacio.s, vertical = FhEspacio.xs)
        ) {
            if (icono != null) {
                Icon(icono, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(FhEspacio.xs))
            }
            Text(texto, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Fila de chips con salto de linea si no caben. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilaChips(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FhEspacio.s),
        verticalArrangement = Arrangement.spacedBy(FhEspacio.xs),
        modifier = modifier.padding(top = FhEspacio.xs)
    ) { contenido() }
}

/** Mensaje destacado (error, aviso o informacion) con accion opcional. Se anuncia a los lectores de pantalla. */
@Composable
internal fun Mensaje(texto: String, tono: Tono, accion: String? = null, enabled: Boolean = true, onAccion: (() -> Unit)? = null) {
    val (fondo, contenido) = coloresTono(tono)
    val icono = when (tono) {
        Tono.ERROR -> if (texto.startsWith("No se pudo conectar") || texto.startsWith("No hay conexión")) FhIcons.SinConexion else FhIcons.Vencido
        Tono.EXITO -> FhIcons.Hecho
        else -> FhIcons.Informacion
    }
    Surface(
        color = fondo, contentColor = contenido, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs).semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(start = FhEspacio.m, end = FhEspacio.xs, top = FhEspacio.s, bottom = FhEspacio.s),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(FhEspacio.s))
            Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (accion != null && onAccion != null) {
                TextButton(onClick = onAccion, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = contenido)) {
                    Text(accion)
                }
            }
        }
    }
}

/** Error con "Reintentar" opcional. */
@Composable
internal fun EstadoError(mensaje: String, enabled: Boolean = true, onReintentar: (() -> Unit)? = null) =
    Mensaje(mensaje, Tono.ERROR, accion = if (onReintentar != null) "Reintentar" else null, enabled = enabled, onAccion = onReintentar)

/** Lista vacia: icono y texto centrados. */
@Composable
internal fun EstadoVacio(texto: String, icono: ImageVector = FhIcons.Bandeja) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xl, horizontal = FhEspacio.l)
    ) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(FhEspacio.s))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Indicador de carga bajo la cabecera. */
@Composable
internal fun EstadoCargando(visible: Boolean) {
    if (visible) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = FhEspacio.xs))
}

internal const val TITULO_MIS_TRABAJOS = "Mis trabajos"

/**
 * Estructura comun de pantalla: TopAppBar de Material 3, flecha atras (descripcion "Volver") en las secundarias y "Salir"
 * en las raiz por rol (Centros, Mis trabajos). Respeta las areas seguras: barra de estado, barra de navegacion inferior y
 * teclado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(
    title: String,
    subtitle: String,
    onAction: () -> Unit,
    scrollable: Boolean = false,
    actionLabel: String? = null,
    accionExtra: (@Composable () -> Unit)? = null,
    // FH-009 / R5: barra fija inferior (p. ej. acciones de validacion). Queda encima de la barra de gestos y del teclado;
    // el contenido desplazable termina por encima de ella, asi que no tapa nada.
    barraInferior: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // FH-009 / R3: en la raiz de una pestana no hay flecha atras; se muestra el menu de cuenta (con «Cerrar sesión»).
    // Fuera de una pestana: «Volver», salvo una etiqueta explicita (p. ej. «Salir» en la pantalla sin permisos).
    val menuCuenta = LocalMenuCuenta.current
    val etiqueta = actionLabel ?: "Volver"
    val volver = menuCuenta == null && etiqueta == "Volver"
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    if (volver) IconButton(onClick = onAction) { Icon(FhIcons.Volver, contentDescription = "Volver") }
                },
                actions = {
                    accionExtra?.invoke()
                    if (menuCuenta != null) menuCuenta() else if (!volver) TextButton(onClick = onAction) { Text(etiqueta) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
        },
        bottomBar = {
            if (barraInferior != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(horizontal = FhEspacio.l, vertical = FhEspacio.s),
                        verticalArrangement = Arrangement.spacedBy(FhEspacio.xs),
                        content = barraInferior
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = FhEspacio.l, vertical = FhEspacio.m),
            content = content
        )
    }
}

/**
 * FH-009 / R6: pestanas internas de una pantalla (no la barra inferior). Con fuente normal ocupan todo el ancho; con
 * fuente grande pasan a desplazables, con cada etiqueta en una linea, para no partir palabras.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PestanasInternas(titulos: List<String>, seleccionada: Int, onSeleccionar: (Int) -> Unit) {
    val grande = LocalDensity.current.fontScale >= 1.3f
    val pestanas: @Composable () -> Unit = {
        titulos.forEachIndexed { i, t ->
            Tab(
                selected = seleccionada == i,
                onClick = { onSeleccionar(i) },
                modifier = Modifier.heightIn(min = 48.dp),
                text = {
                    if (grande) Text(t, maxLines = 1, softWrap = false)
                    else Text(t, textAlign = TextAlign.Center)
                }
            )
        }
    }
    if (grande) {
        PrimaryScrollableTabRow(selectedTabIndex = seleccionada, edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.background, tabs = pestanas)
    } else {
        PrimaryTabRow(selectedTabIndex = seleccionada, containerColor = MaterialTheme.colorScheme.background, tabs = pestanas)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = FhEspacio.xs, bottom = FhEspacio.s)
    )
}
