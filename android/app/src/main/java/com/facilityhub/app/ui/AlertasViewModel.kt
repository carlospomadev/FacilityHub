package com.facilityhub.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facilityhub.app.data.AlertaDto
import com.facilityhub.app.data.FacilityRepository
import com.facilityhub.app.data.NetworkProvider
import com.facilityhub.app.data.TokenStore
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

// FH-006 / P6-D1: bandeja de alertas propias. Solo consulta con sesion iniciada y rol conocido (sin peticiones antes del
// login). El backend filtra siempre por el usuario de la sesion; aqui solo se muestra lo que devuelve.
class AlertasViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val repository = FacilityRepository(NetworkProvider.createApi(tokenStore), tokenStore)

    var alertas by mutableStateOf<List<AlertaDto>>(emptyList()); private set
    var noLeidas by mutableStateOf(0L); private set
    var soloNoLeidas by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var cargada by mutableStateOf(false); private set
    // 401: la pantalla vuelve al login.
    var sesionCaducada by mutableStateOf(false); private set

    private val sesionValida: Boolean
        get() = tokenStore.token != null && tokenStore.role in ROLES_CON_ALERTAS

    // FH-009 / R7: corrutinas de la sesion (se cancelan al salir); la lista en curso se cancela si cambia el filtro.
    private val sesion = AmbitoSesion(this)
    private var trabajoLista: Job? = null
    private var contadorEn = 0L

    // Contador para el icono de las pantallas raiz. Silencioso: un fallo no tapa la pantalla principal.
    // FH-009 / R7: como mucho una peticion cada 15 s al cambiar de pestana (antes, una por cada cambio).
    fun cargarContador(forzar: Boolean = false) {
        if (!sesionValida) return
        val ahora = SystemClock.elapsedRealtime()
        if (!forzar && contadorEn != 0L && ahora - contadorEn < INTERVALO_CONTADOR_MS) return
        contadorEn = ahora
        sesion.scope.launch {
            try {
                noLeidas = repository.alertasNoLeidas()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                contadorEn = 0L
                if (e.code() == 401) sesionCaducada = true
            } catch (_: Exception) {
                // sin conexion: se mantiene el ultimo valor y se reintenta en el siguiente cambio de pestana
                contadorEn = 0L
            }
        }
    }

    fun cargar() {
        if (!sesionValida) return
        trabajoLista?.cancel()
        val solo = soloNoLeidas
        loading = true; error = null
        val nuevo = sesion.scope.launch {
            try {
                // Lista y contador en paralelo (antes en serie).
                coroutineScope {
                    val l = async { repository.alertas(solo) }
                    val n = async { repository.alertasNoLeidas() }
                    alertas = l.await(); noLeidas = n.await()
                }
                contadorEn = SystemClock.elapsedRealtime()
                cargada = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tratar(e)
            } finally {
                if (trabajoLista === coroutineContext[Job]) loading = false
            }
        }
        trabajoLista = nuevo
    }

    fun cambiarFiltro(solo: Boolean) {
        soloNoLeidas = solo
        cargar()
    }

    // Guarda sincrona contra la doble pulsacion. Idempotente en el backend. Tras marcar, ejecuta onHecho (p. ej. navegar).
    fun marcarLeida(alerta: AlertaDto, onHecho: () -> Unit = {}) {
        if (loading) return
        if (alerta.leidaAt != null) { onHecho(); return }
        loading = true; error = null
        sesion.scope.launch {
            try {
                val actualizada = repository.marcarAlertaLeida(alerta.id)
                alertas = if (soloNoLeidas) alertas.filter { it.id != alerta.id } else alertas.map { if (it.id == alerta.id) actualizada else it }
                noLeidas = (noLeidas - 1).coerceAtLeast(0)
                onHecho()
            } catch (e: Exception) {
                tratar(e)
            } finally {
                loading = false
            }
        }
    }

    fun limpiar() {
        sesion.reiniciar(); trabajoLista = null; loading = false; contadorEn = 0L
        alertas = emptyList(); noLeidas = 0; soloNoLeidas = false; error = null; cargada = false; sesionCaducada = false
    }

    fun clearError() { error = null }

    private fun tratar(e: Exception) {
        if (e is HttpException && e.code() == 401) {
            sesionCaducada = true
        }
        error = when {
            e is HttpException && (e.code() == 403 || e.code() == 404) -> "No tienes acceso a esta alerta."
            else -> mensajeError(e)
        }
    }
}

private const val INTERVALO_CONTADOR_MS = 15_000L

private val ROLES_CON_ALERTAS = setOf("ADMIN", "EXPLOTACION", "INQUILINO", "PROVEEDOR")
