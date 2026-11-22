package com.facilityhub.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// FH-009 / R1: paleta de la direccion visual MIXTA aprobada (marca azul petroleo #0F5C6E, superficies neutras).
// El color se reserva para los estados; los tonos semanticos completan el ColorScheme de Material 3.

internal val Petroleo = Color(0xFF0F5C6E)

internal val EsquemaClaro = lightColorScheme(
    primary = Petroleo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8EE),
    onPrimaryContainer = Color(0xFF062F38),
    inversePrimary = Color(0xFF85D2E6),
    secondary = Color(0xFF4A6268),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE7EC),
    onSecondaryContainer = Color(0xFF051F24),
    tertiary = Color(0xFF5B5B7E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1E0FF),
    onTertiaryContainer = Color(0xFF171837),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFDCE4E6),
    onSurfaceVariant = Color(0xFF3F4948),
    surfaceTint = Petroleo,
    inverseSurface = Color(0xFF2E3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFC4CBC8),
    surfaceBright = Color(0xFFFAFAF8),
    surfaceDim = Color(0xFFDADCDA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F5F3),
    surfaceContainer = Color(0xFFEEF0EE),
    surfaceContainerHigh = Color(0xFFE8EAE8),
    surfaceContainerHighest = Color(0xFFE2E4E2)
)

internal val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF85D2E6),
    onPrimary = Color(0xFF003641),
    primaryContainer = Color(0xFF004E5D),
    onPrimaryContainer = Color(0xFFCDE8EE),
    inversePrimary = Petroleo,
    secondary = Color(0xFFB1CBD0),
    onSecondary = Color(0xFF1C3438),
    secondaryContainer = Color(0xFF334B4F),
    onSecondaryContainer = Color(0xFFCDE7EC),
    tertiary = Color(0xFFC4C3EA),
    onTertiary = Color(0xFF2D2D4D),
    tertiaryContainer = Color(0xFF434465),
    onTertiaryContainer = Color(0xFFE1E0FF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111414),
    onBackground = Color(0xFFE1E3E2),
    surface = Color(0xFF111414),
    onSurface = Color(0xFFE1E3E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBFC8C7),
    surfaceTint = Color(0xFF85D2E6),
    inverseSurface = Color(0xFFE1E3E2),
    inverseOnSurface = Color(0xFF2E3131),
    outline = Color(0xFF899392),
    outlineVariant = Color(0xFF3F4948),
    surfaceBright = Color(0xFF373A3A),
    surfaceDim = Color(0xFF111414),
    surfaceContainerLowest = Color(0xFF0C0F0F),
    surfaceContainerLow = Color(0xFF191C1C),
    surfaceContainer = Color(0xFF1D2020),
    surfaceContainerHigh = Color(0xFF272B2A),
    surfaceContainerHighest = Color(0xFF323535)
)

/** Colores semanticos de estado que Material 3 no define (exito, aviso, informativo e historico P5). */
@Immutable
data class ColoresSemanticos(
    val exito: Color, val exitoContenedor: Color, val onExitoContenedor: Color,
    val aviso: Color, val avisoContenedor: Color, val onAvisoContenedor: Color,
    val info: Color, val infoContenedor: Color, val onInfoContenedor: Color,
    val historico: Color, val historicoContenedor: Color, val onHistoricoContenedor: Color
)

internal val SemanticosClaro = ColoresSemanticos(
    exito = Color(0xFF1E6B3A), exitoContenedor = Color(0xFFCDEED6), onExitoContenedor = Color(0xFF0A3A1C),
    aviso = Color(0xFF7A4B00), avisoContenedor = Color(0xFFFFE2B3), onAvisoContenedor = Color(0xFF2B1700),
    info = Color(0xFF3A4E8C), infoContenedor = Color(0xFFDDE3FF), onInfoContenedor = Color(0xFF0E1B4A),
    historico = Color(0xFF6B5E52), historicoContenedor = Color(0xFFEDE6DE), onHistoricoContenedor = Color(0xFF2A211A)
)

internal val SemanticosOscuro = ColoresSemanticos(
    exito = Color(0xFF8FD9A6), exitoContenedor = Color(0xFF0F5128), onExitoContenedor = Color(0xFFCDEED6),
    aviso = Color(0xFFFFB951), avisoContenedor = Color(0xFF5C3A00), onAvisoContenedor = Color(0xFFFFE2B3),
    info = Color(0xFFB7C4FF), infoContenedor = Color(0xFF22356F), onInfoContenedor = Color(0xFFDDE3FF),
    historico = Color(0xFFD6C3B0), historicoContenedor = Color(0xFF4B4036), onHistoricoContenedor = Color(0xFFEDE6DE)
)
