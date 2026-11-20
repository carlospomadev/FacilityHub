package com.facilityhub.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facilityhub.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.HttpException

// FH-006 / P6-C3: circuito de intervenciones (PROVEEDOR y gestion), validacion (EXPLOTACION/ADMIN) y area del
// INQUILINO. Las acciones que la interfaz ofrece salen de accionesPermitidas; el backend sigue siendo la barrera real.
// Toda escritura pasa por una guarda sincrona para ignorar dobles pulsaciones y nunca se reintenta sola.
class FlujoViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val repository = FacilityRepository(NetworkProvider.createApi(tokenStore), tokenStore)

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var aviso by mutableStateOf<String?>(null); private set

    // Proveedor y gestion
    var intervencionesMantenimiento by mutableStateOf<List<IntervencionDto>>(emptyList()); private set
    var intervencion by mutableStateOf<IntervencionDto?>(null); private set
    var pendientes by mutableStateOf<List<IntervencionPendienteDto>>(emptyList()); private set
    var proveedores by mutableStateOf<List<ProveedorDto>>(emptyList()); private set
    var asignacion by mutableStateOf<AsignacionDto?>(null); private set
    var asignacionConsultada by mutableStateOf(false); private set
    var declaraciones by mutableStateOf<List<DeclaracionPendienteDto>>(emptyList()); private set
    var solicitudesGestion by mutableStateOf<List<SolicitudProveedorDto>>(emptyList()); private set

    // Inquilino
    var obligacionesInquilino by mutableStateOf<List<ObligacionInquilinoDto>>(emptyList()); private set
    var mantenimientosInquilino by mutableStateOf<List<MantenimientoInquilinoDetalleDto>>(emptyList()); private set
    var solicitudesInquilino by mutableStateOf<List<SolicitudProveedorDto>>(emptyList()); private set
    var proveedorEncontrado by mutableStateOf<ProveedorBasicoDto?>(null); private set

    fun limpiarMensajes() { error = null; aviso = null }

    // ---------- Lecturas ----------

    fun cargarIntervencionesMantenimiento(mantenimientoId: Long) {
        intervencionesMantenimiento = emptyList()
        lectura { intervencionesMantenimiento = repository.intervenciones(mantenimientoId) }
    }

    fun cargarIntervencion(id: Long) {
        intervencion = null
        lectura { intervencion = repository.intervencion(id) }
    }

    fun cargarPendientes() = lectura { pendientes = repository.pendientesValidacion() }
    fun cargarProveedores() = lectura { proveedores = repository.proveedores() }
    fun cargarDeclaraciones() = lectura { declaraciones = repository.declaracionesPendientes() }
    fun cargarSolicitudesGestion() = lectura { solicitudesGestion = repository.solicitudesGestion() }

    // Asignacion vigente del mantenimiento (gestion). 404 = sin asignacion, no es un error para el usuario.
    fun cargarAsignacion(mantenimientoId: Long) {
        asignacion = null; asignacionConsultada = false
        lectura {
            asignacion = try {
                repository.asignacion(mantenimientoId)
            } catch (e: HttpException) {
                if (e.code() == 404) null else throw e
            }
            asignacionConsultada = true
        }
    }

    fun cargarObligacionesInquilino() = lectura { obligacionesInquilino = repository.obligacionesInquilino() }

    fun cargarMantenimientosInquilino(obligacionId: Long) {
        mantenimientosInquilino = emptyList()
        lectura { mantenimientosInquilino = repository.mantenimientosInquilino(obligacionId) }
    }

    fun cargarSolicitudesInquilino() = lectura { solicitudesInquilino = repository.solicitudesInquilino() }

    // ---------- PROVEEDOR / gestion: intervenciones ----------

    fun registrarIntervencion(mantenimientoId: Long, onCreada: (Long) -> Unit) = escritura {
        val creada = repository.registrarIntervencion(mantenimientoId, DatosIntervencionRequest(null, null, null, null, null))
        intervencion = creada
        aviso = "Borrador creado."
        onCreada(creada.id)
    }

    fun guardarBorrador(actual: IntervencionDto, fecha: String, actuaciones: String, resultado: String?, incidencias: String) = escritura {
        intervencion = repository.actualizarIntervencion(actual.id, DatosIntervencionRequest(
            actual.version, fecha.ifBlank { null }, actuaciones.ifBlank { null }, resultado, incidencias.ifBlank { null }))
        aviso = "Borrador guardado."
    }

    fun adjuntar(actual: IntervencionDto, categoria: String, uri: Uri) = escritura {
        val documento = withContext(Dispatchers.IO) { prepararDocumentoLocal(getApplication(), uri) }
        try {
            intervencion = repository.adjuntarDocumento(actual.id, categoria, documento.nombre, documento.mime, documento.archivo)
            aviso = "Documento adjuntado."
        } finally {
            withContext(Dispatchers.IO) { documento.archivo.delete() }
        }
    }

    // Guarda primero los cambios pendientes del formulario (si los hay) y envia esa version: un segundo envio (o datos
    // desfasados) devuelve 409 y no duplica nada.
    fun guardarYEnviar(actual: IntervencionDto, fecha: String, actuaciones: String, resultado: String?, incidencias: String) = escritura {
        var vigente = actual
        val cambios = fecha.ifBlank { null } != actual.fechaEjecucion || actuaciones.ifBlank { null } != actual.actuaciones
            || resultado != actual.resultado || incidencias.ifBlank { null } != actual.incidencias
        if (cambios) {
            vigente = repository.actualizarIntervencion(actual.id, DatosIntervencionRequest(
                actual.version, fecha.ifBlank { null }, actuaciones.ifBlank { null }, resultado, incidencias.ifBlank { null }))
            intervencion = vigente
        }
        intervencion = repository.enviarIntervencion(vigente.id, vigente.version)
        aviso = "Intervención enviada a validación."
    }

    fun validar(actual: IntervencionDto, accion: String, comentario: String, compartidos: List<Long>) = escritura {
        intervencion = repository.validarIntervencion(actual.id, ValidacionRequest(accion, comentario.ifBlank { null },
            actual.version, if (accion == ACCION_APROBAR) compartidos else null))
        aviso = when (accion) {
            ACCION_APROBAR -> "Intervención aprobada."
            ACCION_CERRAR_SIN_CUMPLIMIENTO -> "Intento cerrado sin cumplimiento."
            else -> "Corrección solicitada."
        }
        pendientes = repository.pendientesValidacion()
    }

    // ---------- Gestion: declaraciones y solicitudes ----------

    fun revisarDeclaracion(d: DeclaracionPendienteDto, aceptar: Boolean, motivo: String) = escritura {
        repository.revisarDeclaracion(d.id, RevisionDeclaracionRequest(if (aceptar) "ACEPTAR" else "RECHAZAR", motivo.ifBlank { null }, d.version))
        aviso = if (aceptar) "Proveedor aceptado." else "Proveedor rechazado."
        declaraciones = repository.declaracionesPendientes()
    }

    fun resolverSolicitud(s: SolicitudProveedorDto, atender: Boolean, proveedorId: Long?, respuesta: String) = escritura {
        repository.resolverSolicitud(s.id, ResolverSolicitudRequest(if (atender) "ATENDER" else "RECHAZAR", proveedorId, respuesta.ifBlank { null }, s.version))
        aviso = if (atender) "Solicitud atendida." else "Solicitud rechazada."
        solicitudesGestion = repository.solicitudesGestion()
    }

    // ---------- INQUILINO ----------

    fun buscarProveedor(cif: String) {
        proveedorEncontrado = null
        lectura { proveedorEncontrado = repository.buscarProveedor(cif.trim()) }
    }

    fun declararProveedor(obligacionId: Long, proveedorId: Long, onHecho: () -> Unit) = escritura {
        repository.declararProveedor(obligacionId, proveedorId)
        aviso = "Proveedor declarado: pendiente de aceptación por Explotación."
        proveedorEncontrado = null
        obligacionesInquilino = repository.obligacionesInquilino()
        onHecho()
    }

    fun solicitarProveedor(request: SolicitudProveedorRequest, onHecho: () -> Unit) = escritura {
        repository.solicitarProveedor(request)
        aviso = "Solicitud enviada. Un administrador dará de alta la empresa."
        solicitudesInquilino = repository.solicitudesInquilino()
        onHecho()
    }

    fun fijarFechaPrevista(m: MantenimientoInquilinoDetalleDto, fecha: String) = escritura {
        repository.fijarFechaPrevista(m.id, fecha.trim(), m.version)
        aviso = "Fecha prevista registrada."
        mantenimientosInquilino = repository.mantenimientosInquilino(m.obligacionId)
    }

    fun asignarProveedorAceptado(m: MantenimientoInquilinoDetalleDto) = escritura {
        repository.asignarProveedorAceptado(m.id)
        aviso = "Proveedor asignado."
        mantenimientosInquilino = repository.mantenimientosInquilino(m.obligacionId)
    }

    // ---------- Descargas (el destino lo elige el usuario con el selector del sistema) ----------

    fun descargarDocumentoIntervencion(intervencionId: Long, documentoId: Long, destino: Uri) =
        escritura { guardar(repository.descargarDocumentoIntervencion(intervencionId, documentoId), destino) }

    fun descargarDocumento(documentoId: Long, destino: Uri) = escritura { guardar(repository.descargarDocumento(documentoId), destino) }

    private suspend fun guardar(cuerpo: ResponseBody, destino: Uri) {
        withContext(Dispatchers.IO) {
            cuerpo.use { body ->
                val salida = getApplication<Application>().contentResolver.openOutputStream(destino)
                    ?: throw IllegalStateException("No se pudo abrir el destino")
                salida.use { body.byteStream().copyTo(it) }
            }
        }
        aviso = "Documento descargado."
    }

    fun limpiarSesion() {
        intervencionesMantenimiento = emptyList(); intervencion = null; pendientes = emptyList(); proveedores = emptyList()
        asignacion = null; declaraciones = emptyList(); solicitudesGestion = emptyList(); obligacionesInquilino = emptyList()
        mantenimientosInquilino = emptyList(); solicitudesInquilino = emptyList(); proveedorEncontrado = null
        error = null; aviso = null
    }

    // ---------- Utilidades ----------

    private fun lectura(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true; error = null
            try { block() } catch (e: Exception) { error = mensajeError(e) } finally { loading = false }
        }
    }

    // Guarda sincrona: se marca ocupado antes de lanzar la corrutina; un segundo toque se ignora.
    private fun escritura(block: suspend () -> Unit) {
        if (loading) return
        loading = true; error = null; aviso = null
        viewModelScope.launch {
            try { block() } catch (e: Exception) { error = mensajeError(e) } finally { loading = false }
        }
    }
}

const val ACCION_REGISTRAR_INTERVENCION = "REGISTRAR_INTERVENCION"
const val ACCION_EDITAR_INTERVENCION = "EDITAR_INTERVENCION"
const val ACCION_ADJUNTAR_DOCUMENTO = "ADJUNTAR_DOCUMENTO"
const val ACCION_ENVIAR_INTERVENCION = "ENVIAR_INTERVENCION"
const val ACCION_APROBAR = "APROBAR"
const val ACCION_SOLICITAR_CORRECCION = "SOLICITAR_CORRECCION"
const val ACCION_CERRAR_SIN_CUMPLIMIENTO = "CERRAR_SIN_CUMPLIMIENTO"
const val ACCION_DECLARAR_PROVEEDOR = "DECLARAR_PROVEEDOR"
const val ACCION_SOLICITAR_PROVEEDOR = "SOLICITAR_PROVEEDOR"
const val ACCION_FIJAR_FECHA_PREVISTA = "FIJAR_FECHA_PREVISTA"
const val ACCION_ASIGNAR_PROVEEDOR = "ASIGNAR_PROVEEDOR"
