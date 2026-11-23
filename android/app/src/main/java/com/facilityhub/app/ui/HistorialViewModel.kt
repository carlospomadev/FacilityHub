package com.facilityhub.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.facilityhub.app.data.FacilityRepository
import com.facilityhub.app.data.HistorialGestionDto
import com.facilityhub.app.data.HistorialInquilinoDto
import com.facilityhub.app.data.NetworkProvider
import com.facilityhub.app.data.TokenStore
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
    // 401: la pantalla vuelve al login.
    var sesionCaducada by mutableStateOf(false); private set

    // FH-009 / R7: estado propio de la lectura (por local) y corrutinas de la sesion. Antes, una peticion en curso hacia
    // ignorar la del local nuevo; ahora la del local nuevo cancela la anterior y una respuesta tardia no la sobrescribe.
    private val sesion = AmbitoSesion(this)
    val lectura = Lectura()
    val loading: Boolean get() = lectura.cargando
    val error: String? get() = lectura.error
    val cargado: Boolean get() = lectura.cargada

    val esGestion: Boolean get() = tokenStore.role in ROLES_PANEL
    private val sesionValida: Boolean
        get() = tokenStore.token != null && (esGestion || tokenStore.role == "INQUILINO")

    /** Pide el historial de [id] si hace falta (otro local, sin datos, con error, cambios hechos desde la app o caducado). */
    fun cargar(id: Long, forzar: Boolean = false) {
        if (!sesionValida) return
        if (!forzar && !lectura.necesitaRecarga(id)) return
        localId = id
        val gestionPedida = esGestion
        lectura.lanzar(
            sesion.scope, id,
            limpiar = { gestion = emptyList(); inquilino = emptyList() },
            peticion = {
                if (gestionPedida) repository.historialGestion(id) to emptyList<HistorialInquilinoDto>()
                else emptyList<HistorialGestionDto>() to repository.historialInquilino(id)
            },
            aplicar = { (g, i) -> gestion = g; inquilino = i },
            onSesionCaducada = { sesionCaducada = true },
            mensaje = ::mensajeHistorial
        )
    }

    fun limpiar() {
        sesion.reiniciar(); lectura.reiniciar()
        localId = null; gestion = emptyList(); inquilino = emptyList(); sesionCaducada = false
    }

    // Sin autorizacion (401/403/404) Lectura deja de mostrar el historial cargado antes; un error de conexion conserva
    // los ultimos datos visibles junto al aviso.
    private fun mensajeHistorial(e: Exception): String = when {
        e is HttpException && e.code() == 403 -> "No tienes permiso para consultar este historial."
        e is HttpException && e.code() == 404 -> "No tienes acceso al historial de este local o no existe."
        else -> mensajeError(e)
    }
}
