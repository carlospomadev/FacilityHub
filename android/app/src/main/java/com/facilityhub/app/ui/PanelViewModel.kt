package com.facilityhub.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facilityhub.app.data.FacilityRepository
import com.facilityhub.app.data.NetworkProvider
import com.facilityhub.app.data.ResumenGestionDto
import com.facilityhub.app.data.TokenStore
import com.facilityhub.app.data.TrabajoGestionDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

// FH-006 / P6-D3-3: panel de Explotacion. Solo lectura: contadores y listados calculados por el backend
// (GET /api/gestion/resumen y /api/gestion/trabajos). Android no recalcula estados ni vencimientos. Solo consulta con
// sesion de gestion (ADMIN o EXPLOTACION); el backend sigue siendo la barrera real (403 para el resto).
class PanelViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val repository = FacilityRepository(NetworkProvider.createApi(tokenStore), tokenStore)

    var resumen by mutableStateOf<ResumenGestionDto?>(null); private set
    // Filtro seleccionado; se conserva al navegar a un mantenimiento y volver.
    var filtro by mutableStateOf(FiltroPanel.VENCIDOS); private set
    var trabajos by mutableStateOf<List<TrabajoGestionDto>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    // Listado cargado para el filtro actual (distingue "vacio" de "aun no cargado").
    var cargado by mutableStateOf(false); private set
    // 401: la pantalla vuelve al login.
    var sesionCaducada by mutableStateOf(false); private set

    private val sesionValida: Boolean
        get() = tokenStore.token != null && tokenStore.role in ROLES_PANEL

    // FH-009 / R4: recarga pedida por el usuario (deslizar o «Actualizar»). Los datos visibles se mantienen hasta que
    // llegan los nuevos; si la recarga falla, siguen visibles junto al aviso de error.
    var refrescando by mutableStateOf(false); private set

    fun refrescar() = cargar(manual = true)

    // Recarga resumen y listado del filtro actual (al abrir el panel y al volver a el).
    fun cargar(manual: Boolean = false) {
        if (!sesionValida || loading) return
        viewModelScope.launch {
            loading = true; error = null; refrescando = manual
            try {
                // coroutineScope: un fallo de red en una de las dos peticiones se propaga a este try (sin cerrar la app).
                coroutineScope {
                    val r = async { repository.resumenGestion() }
                    val t = async { repository.trabajosGestion(filtro.name) }
                    resumen = r.await()
                    trabajos = t.await()
                }
                cargado = true
            } catch (e: Exception) {
                tratar(e)
            } finally {
                loading = false; refrescando = false
            }
        }
    }

    fun seleccionar(nuevo: FiltroPanel) {
        if (!sesionValida || loading) return
        if (nuevo == filtro && cargado) return
        filtro = nuevo
        cargado = false
        trabajos = emptyList()
        viewModelScope.launch {
            loading = true; error = null
            try {
                trabajos = repository.trabajosGestion(nuevo.name)
                cargado = true
            } catch (e: Exception) {
                tratar(e)
            } finally {
                loading = false
            }
        }
    }

    fun limpiar() {
        resumen = null; filtro = FiltroPanel.VENCIDOS; trabajos = emptyList(); error = null; cargado = false
        sesionCaducada = false
    }

    private fun tratar(e: Exception) {
        if (e is HttpException && e.code() == 401) {
            sesionCaducada = true
        }
        error = when {
            e is HttpException && e.code() == 403 -> "No tienes permiso para ver el panel de Explotación."
            else -> mensajeError(e)
        }
    }
}

// Filtros del backend (mismo nombre que el parametro filtro) con su etiqueta.
enum class FiltroPanel(val etiqueta: String) {
    VENCIDOS("Vencidos"),
    PROXIMOS("Próximos a vencer"),
    PENDIENTE_VALIDACION("Pendientes de validación"),
    REQUIERE_CORRECCION("Requieren corrección"),
    SIN_ASIGNAR("Sin asignar")
}

internal val ROLES_PANEL = setOf("ADMIN", "EXPLOTACION")
