package com.facilityhub.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facilityhub.app.data.FacilityRepository
import com.facilityhub.app.data.HistorialGestionDto
import com.facilityhub.app.data.HistorialInquilinoDto
import com.facilityhub.app.data.NetworkProvider
import com.facilityhub.app.data.TokenStore
import kotlinx.coroutines.launch
import retrofit2.HttpException

// FH-006 / P6-D3-4: historial del local (GET /api/locales/{id}/historial). Solo lectura. El backend decide el contenido
// por rol (gestion: completo con historicos P5; INQUILINO: aprobados de su ocupacion vigente) y responde 404 a lo ajeno.
// Android no filtra ni reinterpreta los registros: solo los presenta.
class HistorialViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val repository = FacilityRepository(NetworkProvider.createApi(tokenStore), tokenStore)

    var localId by mutableStateOf<Long?>(null); private set
    var gestion by mutableStateOf<List<HistorialGestionDto>>(emptyList()); private set
    var inquilino by mutableStateOf<List<HistorialInquilinoDto>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var cargado by mutableStateOf(false); private set
    // 401: la pantalla vuelve al login.
    var sesionCaducada by mutableStateOf(false); private set

    val esGestion: Boolean get() = tokenStore.role in ROLES_PANEL
    private val sesionValida: Boolean
        get() = tokenStore.token != null && (esGestion || tokenStore.role == "INQUILINO")

    fun cargar(id: Long) {
        if (!sesionValida || loading) return
        if (id != localId) {
            localId = id; gestion = emptyList(); inquilino = emptyList(); cargado = false
        }
        viewModelScope.launch {
            loading = true; error = null
            try {
                if (esGestion) gestion = repository.historialGestion(id) else inquilino = repository.historialInquilino(id)
                cargado = true
            } catch (e: Exception) {
                tratar(e)
            } finally {
                loading = false
            }
        }
    }

    fun limpiar() {
        localId = null; gestion = emptyList(); inquilino = emptyList(); error = null; cargado = false; sesionCaducada = false
    }

    private fun tratar(e: Exception) {
        if (e is HttpException && e.code() == 401) {
            sesionCaducada = true
        }
        // Sin autorizacion (401/403/404): no se sigue mostrando un historial cargado antes. Un error de conexion
        // conserva los ultimos datos visibles junto al aviso.
        if (e is HttpException && e.code() in setOf(401, 403, 404)) {
            gestion = emptyList(); inquilino = emptyList(); cargado = false
        }
        error = when {
            e is HttpException && e.code() == 403 -> "No tienes permiso para consultar este historial."
            e is HttpException && e.code() == 404 -> "No tienes acceso al historial de este local o no existe."
            else -> mensajeError(e)
        }
    }
}
