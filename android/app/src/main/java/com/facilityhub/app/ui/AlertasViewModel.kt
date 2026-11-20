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

    // Contador para el icono de las pantallas raiz. Silencioso: un fallo no tapa la pantalla principal.
    fun cargarContador() {
        if (!sesionValida) return
        viewModelScope.launch {
            try {
                noLeidas = repository.alertasNoLeidas()
            } catch (e: HttpException) {
                if (e.code() == 401) sesionCaducada = true
            } catch (_: Exception) {
                // sin conexion: se mantiene el ultimo valor
            }
        }
    }

    fun cargar() {
        if (!sesionValida) return
        viewModelScope.launch {
            loading = true; error = null
            try {
                alertas = repository.alertas(soloNoLeidas)
                noLeidas = repository.alertasNoLeidas()
                cargada = true
            } catch (e: Exception) {
                tratar(e)
            } finally {
                loading = false
            }
        }
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
        viewModelScope.launch {
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

private val ROLES_CON_ALERTAS = setOf("ADMIN", "EXPLOTACION", "INQUILINO", "PROVEEDOR")
