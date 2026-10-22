package com.facilityhub.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facilityhub.app.data.*
import kotlinx.coroutines.launch

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

    fun crearMantenimiento(obligacionId: Long, fecha: String, descripcion: String?, onCreated: () -> Unit) = viewModelScope.launch {
        action {
            repository.crearMantenimiento(obligacionId, fecha, descripcion)
            mantenimientos = repository.mantenimientos(obligacionId)
            onCreated()
        }
    }

    fun logout() {
        tokenStore.clear(); loggedIn = false; userName = ""; role = ""
        centros = emptyList(); locales = emptyList(); obligaciones = emptyList(); mantenimientos = emptyList()
    }

    fun clearError() { error = null }

    private suspend fun action(block: suspend () -> Unit) {
        loading = true; error = null
        try { block() } catch (e: Exception) { error = e.message ?: "Error de comunicacion" }
        finally { loading = false }
    }
}
