package com.facilityhub.app.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * FH-009 / R7: version de los datos modificados desde la app. Cada escritura correcta (crear, aprobar, reenviar,
 * asignar...) la incrementa; las lecturas cargadas con una version anterior se consideran desfasadas y se recargan al
 * volver a la pantalla. Solo vive en memoria del proceso (no es una cache persistente).
 */
internal object CambiosDatos {
    var version by mutableLongStateOf(0L)
        private set

    fun marcar() { version++ }
}

/** Antiguedad maxima de unos datos visibles antes de volver a pedirlos al reabrir su pantalla. */
internal const val VIGENCIA_DATOS_MS = 60_000L

/**
 * FH-009 / R7: estado de una lectura de pantalla (UiState localizado). Cada pantalla tiene el suyo, asi que su carga y su
 * error no se mezclan con los de otras. Los datos los guarda el ViewModel; [Lectura] decide cuando aplicarlos:
 *  - misma clave (recarga): los datos visibles se conservan mientras llegan los nuevos ([actualizando]);
 *  - otra clave (otra entidad): se limpian antes, para no mostrar datos de otro centro, local u obligacion;
 *  - una lectura nueva cancela la anterior: una respuesta antigua nunca sobrescribe otra mas reciente;
 *  - 401: [onSesionCaducada]; 403/404: se limpian los datos (no se sigue mostrando algo ya no autorizado).
 */
class Lectura {
    var cargando by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var cargada by mutableStateOf(false); private set
    var clave: Any? = null; private set
    private var cargadaEn = 0L
    private var versionCargada = -1L
    private var trabajo: Job? = null

    /** Hay datos validos visibles y se estan pidiendo de nuevo. */
    val actualizando: Boolean get() = cargando && cargada

    /** True si hay que pedir los datos de [clave]: otra entidad, sin datos, con error, desfasados o caducados. */
    fun necesitaRecarga(clave: Any?, vigenciaMs: Long = VIGENCIA_DATOS_MS): Boolean =
        clave != this.clave || !cargada || error != null || versionCargada != CambiosDatos.version ||
            SystemClock.elapsedRealtime() - cargadaEn > vigenciaMs

    fun <T> lanzar(
        scope: CoroutineScope,
        clave: Any?,
        limpiar: () -> Unit,
        peticion: suspend () -> T,
        aplicar: (T) -> Unit,
        onSesionCaducada: () -> Unit,
        mensaje: (Exception) -> String = ::mensajeError
    ) {
        if (clave != this.clave) {
            trabajo?.cancel()
            limpiar(); cargada = false; this.clave = clave
        } else {
            // Misma entidad ya en curso: no se duplica la peticion.
            if (cargando && trabajo?.isActive == true) return
        }
        error = null; cargando = true
        val version = CambiosDatos.version
        val nuevo = scope.launch {
            try {
                val r = peticion()
                aplicar(r)
                cargada = true; cargadaEn = SystemClock.elapsedRealtime(); versionCargada = version
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401) onSesionCaducada()
                if (e is HttpException && e.code() in setOf(401, 403, 404)) { limpiar(); cargada = false }
                error = mensaje(e)
            } finally {
                if (trabajo === coroutineContext[Job]) cargando = false
            }
        }
        trabajo = nuevo
        if (nuevo.isCompleted) cargando = false
    }

    fun limpiarError() { error = null }

    /** Cancela lo pendiente y olvida el estado (cierre de sesion). */
    fun reiniciar() {
        trabajo?.cancel(); trabajo = null
        cargando = false; error = null; cargada = false; clave = null; cargadaEn = 0L; versionCargada = -1L
    }
}

/**
 * FH-009 / R7: ambito de corrutinas de una sesion. Al cerrar sesion se cancela entero, asi que ninguna respuesta de la
 * sesion anterior llega a escribir datos visibles para la siguiente.
 */
internal class AmbitoSesion(private val vm: ViewModel) {
    var scope: CoroutineScope = nuevo(); private set

    private fun nuevo(): CoroutineScope {
        val padre = vm.viewModelScope.coroutineContext[Job]
        return CoroutineScope(vm.viewModelScope.coroutineContext + SupervisorJob(padre))
    }

    fun reiniciar() { scope.cancel(); scope = nuevo() }
}
