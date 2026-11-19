package com.facilityhub.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facilityhub.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
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

    // Acciones de escritura solo para gestion; rol vacio o desconocido = sin acciones (el backend sigue siendo la barrera real).
    val puedeGestionar: Boolean get() = role in ROLES_GESTION

    // FH-006 / P6-B: el PROVEEDOR solo accede a sus trabajos asignados; nunca a centros ni locales.
    val esProveedor: Boolean get() = role == ROL_PROVEEDOR

    // Pantalla inicial segun el rol (la navegacion por rol no sustituye a los controles del backend).
    val rutaInicio: String get() = if (esProveedor) "trabajos" else "centros"

    var centros by mutableStateOf<List<CentroDto>>(emptyList()); private set
    var locales by mutableStateOf<List<LocalDto>>(emptyList()); private set
    var inquilino by mutableStateOf<InquilinoDto?>(null); private set
    var instalaciones by mutableStateOf<List<InstalacionDto>>(emptyList()); private set
    var obligaciones by mutableStateOf<List<ObligacionDto>>(emptyList()); private set
    var mantenimientos by mutableStateOf<List<MantenimientoDto>>(emptyList()); private set
    var documentos by mutableStateOf<List<DocumentoDto>>(emptyList()); private set
    var trabajos by mutableStateOf<List<TrabajoProveedorDto>>(emptyList()); private set
    var trabajo by mutableStateOf<TrabajoProveedorDto?>(null); private set

    fun login(email: String, password: String, onSuccess: () -> Unit) = run {
        viewModelScope.launch {
            action {
                val result = repository.login(email, password)
                loggedIn = true; userName = result.nombre; role = result.rol
                if (esProveedor) {
                    trabajos = repository.trabajos()
                } else {
                    centros = repository.centros()
                }
                onSuccess()
            }
        }
    }

    fun cargarTrabajos() = viewModelScope.launch { action { trabajos = repository.trabajos() } }

    fun cargarTrabajo(mantenimientoId: Long) {
        trabajo = null
        viewModelScope.launch { action { trabajo = repository.trabajo(mantenimientoId) } }
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
        if (loading || !puedeGestionar) return
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
        if (loading || !puedeGestionar) return
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

    fun cargarDocumentos(mantenimientoId: Long) {
        documentos = emptyList()
        viewModelScope.launch { action { documentos = repository.documentos(mantenimientoId) } }
    }

    fun subirDocumento(mantenimientoId: Long, uri: Uri) {
        // Guarda sincrona: el POST no es idempotente y no se reintenta automaticamente.
        if (loading || !puedeGestionar) return
        loading = true
        viewModelScope.launch {
            action {
                val documento = withContext(Dispatchers.IO) { prepararDocumento(uri) }
                try {
                    repository.subirDocumento(mantenimientoId, documento.nombre, documento.mime, documento.archivo)
                    documentos = repository.documentos(mantenimientoId)
                } finally {
                    withContext(Dispatchers.IO) { documento.archivo.delete() }
                }
            }
        }
    }

    // Valida tipo y tamano y copia el contenido a un temporal con limite de bytes, sin cargarlo en memoria.
    private fun prepararDocumento(uri: Uri): DocumentoLocal {
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        var temporal: File? = null
        try {
            val mime = resolver.getType(uri)
            if (mime == null || mime !in TIPOS_DOCUMENTO) {
                throw DocumentoNoValidoException("Solo se admiten archivos PDF, JPEG o PNG.")
            }

            var nombre = "documento"
            var declarado: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnaNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnaNombre >= 0 && !cursor.isNull(columnaNombre)) nombre = cursor.getString(columnaNombre)
                    val columnaTamano = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (columnaTamano >= 0 && !cursor.isNull(columnaTamano)) declarado = cursor.getLong(columnaTamano)
                }
            }
            val tamano = declarado ?: throw DocumentoNoValidoException("No se pudo determinar el tamaño del archivo.")
            if (tamano == 0L) throw DocumentoNoValidoException("El archivo está vacío.")
            if (tamano > MAX_BYTES_DOCUMENTO) throw DocumentoNoValidoException(MENSAJE_TAMANO_MAXIMO)

            val archivo = File.createTempFile("subida_", null, app.cacheDir)
            temporal = archivo
            val entrada = resolver.openInputStream(uri)
                ?: throw DocumentoNoValidoException("No se pudo abrir el archivo seleccionado.")
            var total = 0L
            entrada.use { origen ->
                archivo.outputStream().use { destino ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val leidos = origen.read(buffer)
                        if (leidos < 0) break
                        total += leidos
                        // No se confia solo en el tamano declarado por el proveedor.
                        if (total > MAX_BYTES_DOCUMENTO) throw DocumentoNoValidoException(MENSAJE_TAMANO_MAXIMO)
                        destino.write(buffer, 0, leidos)
                    }
                }
            }
            if (total == 0L) throw DocumentoNoValidoException("El archivo está vacío.")
            return DocumentoLocal(nombre, mime, archivo)
        } catch (e: Exception) {
            temporal?.delete()
            throw e as? DocumentoNoValidoException
                ?: DocumentoNoValidoException("No se pudo leer el archivo seleccionado.")
        }
    }

    fun logout() {
        tokenStore.clear(); loggedIn = false; userName = ""; role = ""
        centros = emptyList(); locales = emptyList(); obligaciones = emptyList(); mantenimientos = emptyList()
        documentos = emptyList(); trabajos = emptyList(); trabajo = null
    }

    fun clearError() { error = null }

    private suspend fun action(block: suspend () -> Unit) {
        loading = true; error = null
        try { block() } catch (e: Exception) { error = mensajeError(e) }
        finally { loading = false }
    }

    private fun mensajeError(e: Exception) = when (e) {
        is HttpException -> when (e.code()) {
            401 -> "Sesión caducada. Vuelve a iniciar sesión."
            403 -> "No tienes permiso para esta acción."
            404 -> "No se encontró el recurso solicitado."
            413 -> "El servidor rechazó el archivo por superar el tamaño máximo."
            in 500..599 -> "Error del servidor (HTTP ${e.code()}). Inténtalo más tarde."
            else -> "El servidor rechazó la operación (HTTP ${e.code()})"
        }
        is IOException -> "No se pudo conectar con el servidor. Inténtalo de nuevo."
        else -> e.message ?: "Error de comunicacion"
    }
}

private val ROLES_GESTION = setOf("ADMIN", "EXPLOTACION")
private const val ROL_PROVEEDOR = "PROVEEDOR"

const val MAX_BYTES_DOCUMENTO = 20L * 1024 * 1024
val TIPOS_DOCUMENTO = arrayOf("application/pdf", "image/jpeg", "image/png")
private const val MENSAJE_TAMANO_MAXIMO = "El archivo supera el máximo de 20 MB."

private class DocumentoLocal(val nombre: String, val mime: String, val archivo: File)
private class DocumentoNoValidoException(message: String) : Exception(message)

// Transiciones que ofrece la app; el backend no valida el orden.
fun siguienteEstado(estado: String): String? = when (estado) {
    "PROGRAMADO", "PENDIENTE" -> "EN_PROCESO"
    "EN_PROCESO" -> "FINALIZADO"
    else -> null
}
