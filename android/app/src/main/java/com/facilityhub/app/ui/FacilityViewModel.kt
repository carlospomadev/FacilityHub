package com.facilityhub.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facilityhub.app.data.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

class FacilityViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val repository = FacilityRepository(NetworkProvider.createApi(tokenStore), tokenStore)

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var loggedIn by mutableStateOf(tokenStore.token != null); private set
    var userName by mutableStateOf(tokenStore.userName.orEmpty()); private set
    var role by mutableStateOf(tokenStore.role.orEmpty()); private set

    var centros by mutableStateOf<List<CentroDto>>(emptyList()); private set
    var locales by mutableStateOf<List<LocalDto>>(emptyList()); private set
    var inquilino by mutableStateOf<InquilinoDto?>(null); private set
    var instalaciones by mutableStateOf<List<InstalacionDto>>(emptyList()); private set
    var obligaciones by mutableStateOf<List<ObligacionDto>>(emptyList()); private set
    var mantenimientos by mutableStateOf<List<MantenimientoDto>>(emptyList()); private set

    fun login(email: String, password: String, onSuccess: () -> Unit) = run {
        viewModelScope.launch {
            action {
                val result = repository.login(email, password)
                loggedIn = true; userName = result.nombre; role = result.rol
                centros = repository.centros()
                onSuccess()
            }
        }
    }

    fun cargarCentros() = viewModelScope.launch { action { centros = repository.centros() } }
    fun cargarLocales(centroId: Long) = viewModelScope.launch { action { locales = repository.locales(centroId) } }

    fun cargarDetalleLocal(localId: Long) = viewModelScope.launch {
        action {
            inquilino = repository.inquilino(localId)
            instalaciones = repository.instalaciones(localId)
            obligaciones = repository.obligaciones(localId)
        }
    }

    fun cargarMantenimientos(obligacionId: Long) = viewModelScope.launch {
        action { mantenimientos = repository.mantenimientos(obligacionId) }
    }

    fun crearMantenimiento(obligacionId: Long, fecha: String, descripcion: String?, onCreated: () -> Unit) {
        // Guarda sincrona: se marca ocupado antes de lanzar la corrutina para ignorar toques repetidos.
        if (loading) return
        loading = true
        viewModelScope.launch {
            action {
                repository.crearMantenimiento(obligacionId, fecha, descripcion)
                mantenimientos = repository.mantenimientos(obligacionId)
                onCreated()
            }
        }
    }

    fun cambiarEstadoMantenimiento(mantenimiento: MantenimientoDto, nuevoEstado: String, localId: Long) {
        // Misma guarda sincrona que en crearMantenimiento; ademas se valida contra el estado ya cargado.
        if (loading) return
        val actual = mantenimientos.firstOrNull { it.id == mantenimiento.id } ?: return
        if (siguienteEstado(actual.estado) != nuevoEstado) return
        loading = true
        viewModelScope.launch {
            action {
                val fechaRealizacion = if (nuevoEstado == "FINALIZADO") LocalDate.now().toString() else null
                repository.cambiarEstadoMantenimiento(actual.id, nuevoEstado, fechaRealizacion, actual.observaciones)
                mantenimientos = repository.mantenimientos(actual.obligacionId)
                obligaciones = repository.obligaciones(localId)
            }
        }
    }

    fun logout() {
        tokenStore.clear(); loggedIn = false; userName = ""; role = ""
        centros = emptyList(); locales = emptyList(); obligaciones = emptyList(); mantenimientos = emptyList()
    }

    fun clearError() { error = null }

    private suspend fun action(block: suspend () -> Unit) {
        loading = true; error = null
        try { block() } catch (e: Exception) { error = mensajeError(e) }
        finally { loading = false }
    }

    private fun mensajeError(e: Exception) = when (e) {
        is HttpException -> "El servidor rechazó la operación (HTTP ${e.code()})"
        is IOException -> "No se pudo conectar con el servidor. Inténtalo de nuevo."
        else -> e.message ?: "Error de comunicacion"
    }
}

// Transiciones que ofrece la app; el backend no valida el orden.
fun siguienteEstado(estado: String): String? = when (estado) {
    "PROGRAMADO", "PENDIENTE" -> "EN_PROCESO"
    "EN_PROCESO" -> "FINALIZADO"
    else -> null
}
