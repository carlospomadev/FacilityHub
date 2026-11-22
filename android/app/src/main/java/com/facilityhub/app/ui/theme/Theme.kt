package com.facilityhub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// FH-009 / R1: tema de FacilityHub (claro y oscuro) y tokens reutilizables. Sin color dinamico: se conserva la marca.

/** Espaciados de la interfaz (multiplos de 4 dp). */
object FhEspacio {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Formas: 8 dp chips, 12 dp tarjetas, 16 dp indicadores y paneles. */
internal val FhFormas = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Tipografia M3 (fuente del sistema) con titulos algo mas firmes para la jerarquia de las tarjetas.
private val base = Typography()
internal val FhTipografia = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val LocalColoresSemanticos = staticCompositionLocalOf { SemanticosClaro }

/** Acceso a los colores semanticos: `FhTema.semanticos.exito`, etc. */
object FhTema {
    val semanticos: ColoresSemanticos
        @Composable @ReadOnlyComposable get() = LocalColoresSemanticos.current
}

@Composable
fun FacilityHubTheme(oscuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalColoresSemanticos provides if (oscuro) SemanticosOscuro else SemanticosClaro) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
            typography = FhTipografia,
            shapes = FhFormas,
            content = content
        )
    }
}
