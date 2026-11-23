package com.facilityhub.app.ui

import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.facilityhub.app.ui.theme.FhIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign

/**
 * FH-009 / R7: campo de fecha con el selector de Material 3 (DatePickerDialog) en lugar de escribir AAAA-MM-DD.
 *  - [valor] y [onValor] usan siempre ISO AAAA-MM-DD (lo que espera la API); vacio = sin fecha.
 *  - El campo muestra la fecha legible (p. ej. «12 mar 2026») y se abre tocandolo o con el boton «Elegir fecha».
 *  - El selector trabaja en milisegundos UTC: la conversion LocalDate <-> millis se hace en UTC para no desplazar el dia
 *    por la zona horaria del dispositivo.
 *  - Alternativa accesible: el propio selector permite cambiar a introducir la fecha con el teclado (icono de lapiz).
 *  - Con [opcional], un boton «Quitar fecha» deja el campo vacio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampoFecha(
    valor: String,
    onValor: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    opcional: Boolean = false,
    soporte: String? = null
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    val fecha = remember(valor) { runCatching { LocalDate.parse(valor.take(10)) }.getOrNull() }
    val legible = fecha?.let { fechaCorta(it.toString()) } ?: ""

    // Tocar el campo (de solo lectura) abre el selector, igual que el boton del calendario.
    val interaccion = remember { MutableInteractionSource() }
    LaunchedEffect(interaccion, enabled) {
        interaccion.interactions.collect { if (it is PressInteraction.Release && enabled) abierto = true }
    }

    OutlinedTextField(
        value = legible,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(etiqueta) },
        placeholder = { Text("Sin fecha") },
        supportingText = soporte?.let { s -> { Text(s) } },
        interactionSource = interaccion,
        trailingIcon = {
            Row {
                if (opcional && fecha != null) {
                    IconButton(onClick = { onValor("") }, enabled = enabled) {
                        Icon(FhIcons.Cerrar, contentDescription = "Quitar fecha")
                    }
                }
                IconButton(onClick = { abierto = true }, enabled = enabled) {
                    Icon(FhIcons.Calendario, contentDescription = "Elegir fecha")
                }
            }
        },
        modifier = modifier.fillMaxWidth().semantics {
            stateDescription = if (fecha != null) legible else "Sin fecha"
            onClick(label = "Elegir fecha") { if (enabled) abierto = true; enabled }
        }
    )

    if (abierto) {
        // La app esta en espanol: el calendario (mes, dias de la semana, lunes como primer dia y lectura accesible de cada
        // dia) se formatea en espanol aunque el sistema este en otro idioma; titulo y fecha destacada se fijan abajo.
        val configuracion = LocalConfiguration.current
        val configuracionEs = remember(configuracion) { Configuration(configuracion).apply { setLocale(LOCALE_ES) } }
        CompositionLocalProvider(LocalConfiguration provides configuracionEs) {
            SelectorFecha(fecha, onValor) { abierto = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorFecha(fecha: LocalDate?, onValor: (String) -> Unit, cerrar: () -> Unit) {
    val estado = rememberDatePickerState(initialSelectedDateMillis = fecha?.let { aMillisUtc(it) })
    DatePickerDialog(
        onDismissRequest = cerrar,
        confirmButton = {
            TextButton(
                onClick = {
                    estado.selectedDateMillis?.let { onValor(deMillisUtc(it).toString()) }
                    cerrar()
                },
                enabled = estado.selectedDateMillis != null
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
    ) {
        DatePicker(
            state = estado,
            showModeToggle = true,
            title = {
                // FH-009 / R7: con fuente grande, el rotulo del mes de la cabecera de Material 3 ocupa todo el ancho y deja
                // fuera la flecha «mes siguiente». En el lugar del titulo se ponen controles propios, siempre visibles, que
                // mueven el mes mostrado con la API publica (DatePickerState.displayedMonthMillis). Sustituyen al titulo
                // para no restar altura al calendario (con titulo y controles, las filas se solapaban al 200 %).
                val grande = LocalDensity.current.fontScale >= 1.3f
                Column(Modifier.padding(start = if (grande) 12.dp else 24.dp, end = 12.dp, top = if (grande) 8.dp else 16.dp)) {
                    if (grande && estado.displayMode == DisplayMode.Picker) NavegacionMes(estado) else Text("Selecciona una fecha")
                }
            },
            headline = {
                Text(
                    estado.selectedDateMillis?.let { fechaCorta(deMillisUtc(it).toString()) } ?: "Sin fecha",
                    maxLines = 1,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                )
            }
        )
    }
}

/** Mes anterior / mes siguiente para fuente grande, dentro del rango de anos del selector. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavegacionMes(estado: DatePickerState) {
    val mes = deMillisUtc(estado.displayedMonthMillis).withDayOfMonth(1)
    val primero = LocalDate.of(estado.yearRange.first, 1, 1)
    val ultimo = LocalDate.of(estado.yearRange.last, 12, 1)
    val rotulo = mes.format(DateTimeFormatter.ofPattern("LLLL yyyy", LOCALE_ES)).replaceFirstChar { it.uppercase(LOCALE_ES) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { estado.displayedMonthMillis = aMillisUtc(mes.minusMonths(1)) }, enabled = mes > primero) {
            Icon(FhIcons.Anterior, contentDescription = "Mes anterior")
        }
        Text(
            rotulo, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
        )
        IconButton(onClick = { estado.displayedMonthMillis = aMillisUtc(mes.plusMonths(1)) }, enabled = mes < ultimo) {
            Icon(FhIcons.Siguiente, contentDescription = "Mes siguiente")
        }
    }
}

private val LOCALE_ES = Locale.forLanguageTag("es-ES")

internal fun aMillisUtc(fecha: LocalDate): Long = fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun deMillisUtc(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
