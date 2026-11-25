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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.LocalDate

class FacilityViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val repository = FacilityRepository(NetworkProvider.createApi(tokenStore), tokenStore)
    // FH-009 / R7: corrutinas de la sesion actual (se cancelan al salir).
    private val sesion = AmbitoSesion(this)

    // FH-009 / R7: loading/error quedan para el login y las escrituras (con sus guardas contra dobles envios). Cada
    // lectura de pantalla tiene su propio estado (Lectura): su carga y su error no aparecen en otras pantallas.
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var loggedIn by mutableStateOf(tokenStore.token != null); private set
    var userName by mutableStateOf(tokenStore.userName.orEmpty()); private set
    var role by mutableStateOf(tokenStore.role.orEmpty()); private set

    // Acciones de escritura solo para gestion; rol vacio o desconocido = sin acciones (el backend sigue siendo la barrera real).
    val puedeGestionar: Boolean get() = role in ROLES_GESTION

    // FH-006 / P6-B: el PROVEEDOR solo accede a sus trabajos asignados; nunca a centros ni locales.
    val esProveedor: Boolean get() = role == ROL_PROVEEDOR
    val esInquilino: Boolean get() = role == ROL_INQUILINO
    val esAdmin: Boolean get() = role == "ADMIN"

    // Pantalla inicial segun el rol. FH-006 / P6-C3: denegacion por defecto; un rol ausente o desconocido no entra en
    // ninguna pantalla de datos (la navegacion por rol no sustituye a los controles del backend).
    // FH-009 / R3: gestion -> Panel de Explotacion; PROVEEDOR -> Mis trabajos; INQUILINO -> Mis obligaciones (Mi local).
    val rutaInicio: String get() = pestanasDeRol(role).firstOrNull()?.ruta ?: "sin-permisos"

    // FH-009 / R3: un 401 fuera del login indica sesion caducada o token no valido; la app vuelve al login.
    var sesionCaducada by mutableStateOf(false); private set
    // Aviso informativo para la pantalla de login (p. ej. tras caducar la sesion). Se borra al intentar entrar.
    var avisoLogin by mutableStateOf<String?>(null); private set

    var centros by mutableStateOf<List<CentroDto>>(emptyList()); private set
    var locales by mutableStateOf<List<LocalDto>>(emptyList()); private set
    var inquilino by mutableStateOf<InquilinoDto?>(null); private set
    var instalaciones by mutableStateOf<List<InstalacionDto>>(emptyList()); private set
    var obligaciones by mutableStateOf<List<ObligacionDto>>(emptyList()); private set
    var mantenimientos by mutableStateOf<List<MantenimientoDto>>(emptyList()); private set
    var documentos by mutableStateOf<List<DocumentoDto>>(emptyList()); private set
    var trabajos by mutableStateOf<List<TrabajoProveedorDto>>(emptyList()); private set
    var trabajo by mutableStateOf<TrabajoProveedorDto?>(null); private set

    // FH-009 / R7: estado propio de cada lectura (pantalla).
    val lecCentros = Lectura()
    val lecLocales = Lectura()
    val lecLocal = Lectura()
    val lecMantenimientos = Lectura()
    val lecDocumentos = Lectura()
    val lecTrabajos = Lectura()
    val lecTrabajo = Lectura()
    // FH-010A / RF-07: instalaciones del centro (gestion).
    var instalacionesCentro by mutableStateOf<List<InstalacionCentroDto>>(emptyList()); private set
    val lecInstalacionesCentro = Lectura()
    // Alta de instalacion: estado propio (no comparte loading/error con otras escrituras ni con las lecturas).
    var guardandoInstalacion by mutableStateOf(false); private set
    var errorInstalacion by mutableStateOf<ErrorAltaInstalacion?>(null); private set
    // Confirmacion visible en la lista de destino tras un alta correcta; la pantalla la consume al mostrarla.
    var avisoInstalacion by mutableStateOf<String?>(null); private set
    // FH-010B / RF-09: catalogo de tipos activos (del backend; no se duplica en la app) y obligaciones del centro.
    var tiposMantenimiento by mutableStateOf<List<TipoMantenimientoDto>>(emptyList()); private set
    val lecTipos = Lectura()
    var obligacionesCentro by mutableStateOf<List<ObligacionCentroDto>>(emptyList()); private set
    val lecObligacionesCentro = Lectura()
    var guardandoObligacion by mutableStateOf(false); private set
    var errorObligacion by mutableStateOf<ErrorAltaObligacion?>(null); private set
    var avisoObligacion by mutableStateOf<String?>(null); private set
    // FH-012 / RF-08: gestion del catalogo de tipos (consulta: gestion; escritura: solo ADMIN).
    var catalogoTipos by mutableStateOf<List<TipoGestionDto>>(emptyList()); private set
    val lecCatalogoTipos = Lectura()
    var tipoEditado by mutableStateOf<TipoGestionDto?>(null); private set
    val lecTipoEditado = Lectura()
    var guardandoTipo by mutableStateOf(false); private set
    var errorTipo by mutableStateOf<ErrorGestionTipo?>(null); private set
    var avisoTipo by mutableStateOf<String?>(null); private set
    private val lecturas = listOf(lecCentros, lecLocales, lecLocal, lecMantenimientos, lecDocumentos, lecTrabajos, lecTrabajo,
        lecInstalacionesCentro, lecTipos, lecObligacionesCentro, lecCatalogoTipos, lecTipoEditado)
    private val caducar: () -> Unit = { if (loggedIn) sesionCaducada = true }

    // FH-009 / R2: true cuando el ultimo intento de login fallo por credenciales (los campos se marcan en error).
    var credencialesIncorrectas by mutableStateOf(false); private set

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        // Guarda sincrona contra envios duplicados (doble toque, tecla Entrar + boton).
        if (loading) return
        loading = true; error = null; credencialesIncorrectas = false; avisoLogin = null; sesionCaducada = false
        sesion.scope.launch {
            try {
                // Limite propio solo para la autenticacion (no cambia el cliente HTTP del resto de pantallas): ante un
                // servidor inalcanzable el aviso llega en segundos y no tras el tiempo de espera por defecto.
                val result = try {
                    withTimeout(LOGIN_TIMEOUT_MS) { repository.login(email.trim(), password) }
                } catch (e: TimeoutCancellationException) {
                    error = MENSAJE_SIN_CONEXION; return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    credencialesIncorrectas = e is HttpException && e.code() in setOf(400, 401)
                    error = mensajeErrorLogin(e); return@launch
                }
                // FH-009 / R7: sin precarga aqui; la pantalla inicial de cada rol pide sus datos (antes «Mis trabajos»
                // se pedia dos veces tras el login).
                loggedIn = true; userName = result.nombre; role = result.rol
                onSuccess()
            } finally {
                loading = false
            }
        }
    }

    // ---------- Lecturas (cada una con su estado; ver Lectura) ----------

    fun cargarTrabajos(forzar: Boolean = false) {
        if (!forzar && !lecTrabajos.necesitaRecarga("trabajos")) return
        lecTrabajos.lanzar(sesion.scope, "trabajos", { trabajos = emptyList() }, { repository.trabajos() }, { trabajos = it }, caducar)
    }

    fun cargarTrabajo(mantenimientoId: Long) =
        lecTrabajo.lanzar(sesion.scope, mantenimientoId, { trabajo = null }, { repository.trabajo(mantenimientoId) }, { trabajo = it }, caducar)

    // Los centros cambian poco: se piden al abrir la pestana si no estan cargados o han caducado.
    fun cargarCentros(forzar: Boolean = false) {
        if (!forzar && !lecCentros.necesitaRecarga("centros")) return
        lecCentros.lanzar(sesion.scope, "centros", { centros = emptyList() }, { repository.centros() }, { centros = it }, caducar)
    }

    fun cargarLocales(centroId: Long, forzar: Boolean = false) {
        if (!forzar && !lecLocales.necesitaRecarga(centroId)) return
        lecLocales.lanzar(sesion.scope, centroId, { locales = emptyList() }, { repository.locales(centroId) }, { locales = it }, caducar)
    }

    // Tres peticiones independientes en paralelo (antes en serie); se aplican juntas o ninguna.
    fun cargarDetalleLocal(localId: Long, forzar: Boolean = false) {
        if (!forzar && !lecLocal.necesitaRecarga(localId)) return
        lecLocal.lanzar(
            sesion.scope, localId,
            limpiar = { inquilino = null; instalaciones = emptyList(); obligaciones = emptyList() },
            peticion = {
                coroutineScope {
                    val inq = async { repository.inquilino(localId) }
                    val ins = async { repository.instalaciones(localId) }
                    val obl = async { repository.obligaciones(localId) }
                    Triple(inq.await(), ins.await(), obl.await())
                }
            },
            aplicar = { (inq, ins, obl) -> inquilino = inq; instalaciones = ins; obligaciones = obl },
            onSesionCaducada = caducar
        )
    }

    fun cargarMantenimientos(obligacionId: Long, forzar: Boolean = false) {
        if (!forzar && !lecMantenimientos.necesitaRecarga(obligacionId)) return
        lecMantenimientos.lanzar(sesion.scope, obligacionId, { mantenimientos = emptyList() },
            { repository.mantenimientos(obligacionId) }, { mantenimientos = it }, caducar)
    }

    fun cargarDocumentos(mantenimientoId: Long, forzar: Boolean = false) {
        if (!forzar && !lecDocumentos.necesitaRecarga(mantenimientoId)) return
        lecDocumentos.lanzar(sesion.scope, mantenimientoId, { documentos = emptyList() },
            { repository.documentos(mantenimientoId) }, { documentos = it }, caducar)
    }

    fun cargarInstalacionesCentro(centroId: Long, forzar: Boolean = false) {
        if (!puedeGestionar) return
        if (!forzar && !lecInstalacionesCentro.necesitaRecarga(centroId)) return
        lecInstalacionesCentro.lanzar(sesion.scope, centroId, { instalacionesCentro = emptyList() },
            { repository.instalacionesCentro(centroId) }, { instalacionesCentro = it }, caducar)
    }

    // ---------- FH-010A / RF-07: alta de instalacion ----------

    /**
     * Registra una instalacion. Guarda sincrona contra dobles envios (el POST no es idempotente). Si falla, el formulario
     * conserva sus datos: el error se expone en [errorInstalacion] (409 = duplicado, ligado al campo Nombre). Tras un alta
     * correcta se invalidan las lecturas ([CambiosDatos]) para que la lista de destino se recargue al volver.
     */
    fun registrarInstalacion(centroId: Long, peticion: CrearInstalacionRequest, onCreada: (InstalacionCentroDto) -> Unit) {
        if (guardandoInstalacion || !puedeGestionar) return
        guardandoInstalacion = true; errorInstalacion = null
        sesion.scope.launch {
            try {
                val creada = repository.crearInstalacion(centroId, peticion)
                CambiosDatos.marcar()
                val ubicacion = if (creada.localId == null) "la zona común"
                    else "el local ${creada.localNombre ?: creada.localCodigo}"
                avisoInstalacion = "Instalación «${creada.nombre}» registrada en $ubicacion."
                onCreada(creada)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorInstalacion = errorAltaInstalacion(e)
                if (e is HttpException && e.code() == 401 && loggedIn) sesionCaducada = true
            } finally {
                guardandoInstalacion = false
            }
        }
    }

    // ---------- FH-010B / RF-09: obligaciones ----------

    fun cargarTipos(forzar: Boolean = false) {
        if (!puedeGestionar) return
        if (!forzar && !lecTipos.necesitaRecarga("tipos")) return
        lecTipos.lanzar(sesion.scope, "tipos", { tiposMantenimiento = emptyList() }, { repository.tiposMantenimiento() },
            { tiposMantenimiento = it }, caducar)
    }

    fun cargarObligacionesCentro(centroId: Long, forzar: Boolean = false) {
        if (!puedeGestionar) return
        if (!forzar && !lecObligacionesCentro.necesitaRecarga(centroId)) return
        lecObligacionesCentro.lanzar(sesion.scope, centroId, { obligacionesCentro = emptyList() },
            { repository.obligacionesCentro(centroId) }, { obligacionesCentro = it }, caducar)
    }

    /**
     * Registra una obligacion (D2: solo la obligacion; el primer mantenimiento se programa despues con el flujo de RF-10).
     * Misma guarda contra dobles envios y el mismo tratamiento de errores que el alta de instalaciones.
     */
    fun registrarObligacion(centroId: Long, peticion: CrearObligacionRequest, onCreada: (ObligacionCentroDto) -> Unit) {
        if (guardandoObligacion || !puedeGestionar) return
        guardandoObligacion = true; errorObligacion = null
        sesion.scope.launch {
            try {
                val creada = repository.crearObligacion(centroId, peticion)
                CambiosDatos.marcar()
                avisoObligacion = "Obligación registrada correctamente. El primer mantenimiento está pendiente de programar."
                onCreada(creada)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorObligacion = errorAltaObligacion(e)
                if (e is HttpException && e.code() == 401 && loggedIn) sesionCaducada = true
            } finally {
                guardandoObligacion = false
            }
        }
    }

    fun limpiarErrorObligacion() { errorObligacion = null }

    // ---------- FH-012 / RF-08: catalogo de tipos ----------

    fun cargarCatalogoTipos(forzar: Boolean = false) {
        if (!puedeGestionar) return
        if (!forzar && !lecCatalogoTipos.necesitaRecarga("catalogo")) return
        lecCatalogoTipos.lanzar(sesion.scope, "catalogo", { catalogoTipos = emptyList() }, { repository.catalogoTipos() },
            { catalogoTipos = it }, caducar)
    }

    /** Siempre pide el tipo al backend: la huella debe ser la actual al abrir (o actualizar) el formulario. */
    fun cargarTipoEditado(id: Long) {
        if (!esAdmin) return
        lecTipoEditado.lanzar(sesion.scope, id, { tipoEditado = null }, { repository.tipo(id) }, { tipoEditado = it }, caducar)
    }

    fun crearTipo(peticion: CrearTipoRequest, onHecho: (TipoGestionDto) -> Unit) =
        escrituraTipo({ repository.crearTipo(peticion) }, { "Tipo «${it.nombre}» registrado." }, onHecho)

    fun editarTipo(id: Long, peticion: EditarTipoRequest, onHecho: (TipoGestionDto) -> Unit) =
        escrituraTipo({ repository.editarTipo(id, peticion) }, { "Tipo «${it.nombre}» actualizado." }, onHecho)

    fun cambiarActivoTipo(id: Long, peticion: CambiarActivoTipoRequest, onHecho: (TipoGestionDto) -> Unit) =
        escrituraTipo({ repository.cambiarActivoTipo(id, peticion) },
            { if (it.activo) "Tipo «${it.nombre}» reactivado."
              else "Tipo «${it.nombre}» desactivado. Ya no se ofrece para obligaciones nuevas." },
            onHecho)

    // Guarda sincrona contra dobles envios; el formulario conserva sus datos si falla. Tras guardar, CambiosDatos invalida
    // el catalogo y la lista de tipos activos de «Nueva obligación».
    private fun escrituraTipo(peticion: suspend () -> TipoGestionDto, aviso: (TipoGestionDto) -> String,
                              onHecho: (TipoGestionDto) -> Unit) {
        if (guardandoTipo || !esAdmin) return
        guardandoTipo = true; errorTipo = null
        sesion.scope.launch {
            try {
                val tipo = peticion()
                CambiosDatos.marcar()
                tipoEditado = tipo
                avisoTipo = aviso(tipo)
                onHecho(tipo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorTipo = errorGestionTipo(e)
                if (e is HttpException && e.code() == 401 && loggedIn) sesionCaducada = true
            } finally {
                guardandoTipo = false
            }
        }
    }

    fun limpiarErrorTipo() { errorTipo = null }
    fun consumirAvisoTipo() { avisoTipo = null }
    fun consumirAvisoObligacion() { avisoObligacion = null }

    fun limpiarErrorInstalacion() { errorInstalacion = null }
    fun consumirAvisoInstalacion() { avisoInstalacion = null }

    // ---------- Escrituras (loading/error compartidos solo entre escrituras; guarda sincrona) ----------

    fun crearMantenimiento(obligacionId: Long, fecha: String, descripcion: String?, onCreated: () -> Unit) {
        // Guarda sincrona: se marca ocupado antes de lanzar la corrutina para ignorar toques repetidos.
        if (loading || !puedeGestionar) return
        loading = true
        sesion.scope.launch {
            operacion {
                repository.crearMantenimiento(obligacionId, fecha, descripcion)
                CambiosDatos.marcar()
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
        sesion.scope.launch {
            operacion {
                val fechaRealizacion = if (nuevoEstado == "FINALIZADO") LocalDate.now().toString() else null
                repository.cambiarEstadoMantenimiento(actual.id, nuevoEstado, fechaRealizacion, actual.observaciones)
                CambiosDatos.marcar()
                // Recargas independientes en paralelo (antes en serie).
                coroutineScope {
                    val m = async { repository.mantenimientos(actual.obligacionId) }
                    val o = async { repository.obligaciones(localId) }
                    mantenimientos = m.await(); obligaciones = o.await()
                }
            }
        }
    }

    fun subirDocumento(mantenimientoId: Long, uri: Uri) {
        // Guarda sincrona: el POST no es idempotente y no se reintenta automaticamente.
        if (loading || !puedeGestionar) return
        loading = true
        sesion.scope.launch {
            operacion {
                val documento = withContext(Dispatchers.IO) { prepararDocumento(uri) }
                try {
                    repository.subirDocumento(mantenimientoId, documento.nombre, documento.mime, documento.archivo)
                    CambiosDatos.marcar()
                    documentos = repository.documentos(mantenimientoId)
                } finally {
                    withContext(Dispatchers.IO) { documento.archivo.delete() }
                }
            }
        }
    }

    private fun prepararDocumento(uri: Uri): DocumentoLocal = prepararDocumentoLocal(getApplication(), uri)

    /** Cierra la sesion local (token y datos). Con [caducada], el login muestra el motivo. No llama a la API. */
    fun logout(caducada: Boolean = false) {
        // FH-009 / R7: se cancelan las peticiones en curso de la sesion; ninguna respuesta tardia llega a la siguiente.
        sesion.reiniciar()
        lecturas.forEach { it.reiniciar() }
        sesionCaducada = false; loading = false
        avisoLogin = if (caducada) "Tu sesión ha caducado. Vuelve a iniciar sesión." else null
        tokenStore.clear(); loggedIn = false; userName = ""; role = ""
        centros = emptyList(); locales = emptyList(); obligaciones = emptyList(); mantenimientos = emptyList()
        documentos = emptyList(); trabajos = emptyList(); trabajo = null
        // FH-006 / P6-D3-6: sin restos de la sesion anterior en el login (p. ej. un error de conexion previo a un 401).
        inquilino = null; instalaciones = emptyList(); error = null; credencialesIncorrectas = false
        instalacionesCentro = emptyList(); guardandoInstalacion = false; errorInstalacion = null; avisoInstalacion = null
        tiposMantenimiento = emptyList(); obligacionesCentro = emptyList(); guardandoObligacion = false
        errorObligacion = null; avisoObligacion = null
        catalogoTipos = emptyList(); tipoEditado = null; guardandoTipo = false; errorTipo = null; avisoTipo = null
    }

    fun clearError() { error = null; credencialesIncorrectas = false }

    private suspend fun operacion(block: suspend () -> Unit) {
        loading = true; error = null
        try { block() } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = mensajeError(e)
            if (e is HttpException && e.code() == 401 && loggedIn) sesionCaducada = true
        }
        finally { loading = false }
    }
}

// FH-006 / P6-C3: compartido con FlujoViewModel. Valida tipo y tamano y copia el contenido a un temporal con limite
// de bytes, sin cargarlo en memoria.
internal fun prepararDocumentoLocal(app: Application, uri: Uri): DocumentoLocal {
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

// Mensajes de error comunes. 400 y 409 muestran el detalle del backend (regla de negocio o conflicto de version).
// FH-009 / R2: mensajes propios del login. Un 401 aqui son credenciales, no una sesion caducada; no se distingue si
// falla el correo o la contrasena (no se revela que cuentas existen).
private const val LOGIN_TIMEOUT_MS = 6_000L
internal const val MENSAJE_SIN_CONEXION =
    "No hay conexión con el servidor. Comprueba que el servicio está activo y vuelve a intentarlo."

internal fun mensajeErrorLogin(e: Exception): String = when {
    e is HttpException && e.code() in setOf(400, 401) -> "Correo o contraseña incorrectos."
    e is HttpException && e.code() == 429 -> "Demasiados intentos. Espera un momento y vuelve a intentarlo."
    e is HttpException && e.code() >= 500 -> "El servidor no está disponible en este momento. Inténtalo más tarde."
    e is IOException -> MENSAJE_SIN_CONEXION
    else -> mensajeError(e)
}

internal fun mensajeError(e: Exception): String = when (e) {
    is HttpException -> when (e.code()) {
        400 -> detalleError(e) ?: "Datos no válidos."
        401 -> "Sesión caducada. Vuelve a iniciar sesión."
        403 -> "No tienes permiso para esta acción."
        404 -> "No se encontró el recurso solicitado."
        409 -> "Conflicto: " + (detalleError(e) ?: "otro usuario modificó el registro o la acción ya no es válida.") + " Recarga los datos."
        413 -> "El servidor rechazó el archivo por superar el tamaño máximo."
        in 500..599 -> "Error del servidor (HTTP ${e.code()}). Inténtalo más tarde."
        else -> "El servidor rechazó la operación (HTTP ${e.code()})"
    }
    is IOException -> "No se pudo conectar con el servidor. Inténtalo de nuevo."
    else -> e.message ?: "Error de comunicacion"
}

/** FH-010A: error del alta de instalacion. [duplicado] = el nombre ya existe en esa ubicacion (se marca el campo Nombre). */
data class ErrorAltaInstalacion(val mensaje: String, val duplicado: Boolean = false)

// FH-010A: traduce los rechazos conocidos del alta (el cuerpo de error solo se puede leer una vez).
internal fun errorAltaInstalacion(e: Exception): ErrorAltaInstalacion {
    if (e !is HttpException || e.code() !in setOf(400, 404, 409)) return ErrorAltaInstalacion(mensajeError(e))
    val detalle = detalleError(e).orEmpty()
    return when {
        e.code() == 409 && detalle.startsWith("Ya existe una instalacion") ->
            ErrorAltaInstalacion("Ya existe una instalación con ese nombre en esta ubicación.", duplicado = true)
        detalle.startsWith("El centro esta inactivo") -> ErrorAltaInstalacion("El centro está inactivo y no admite nuevas instalaciones.")
        detalle.startsWith("El local esta inactivo") -> ErrorAltaInstalacion("El local está inactivo y no admite nuevas instalaciones.")
        detalle.startsWith("El local no pertenece") -> ErrorAltaInstalacion("El local elegido no pertenece a este centro.")
        e.code() == 404 -> ErrorAltaInstalacion("El centro o el local ya no existe. Vuelve atrás y recarga los datos.")
        e.code() == 400 -> ErrorAltaInstalacion("Datos no válidos. Revisa los campos marcados.")
        else -> ErrorAltaInstalacion("No se pudo registrar: la operación entra en conflicto con los datos actuales. Recarga los datos.")
    }
}

/** FH-010B: campo del formulario al que se liga un rechazo del alta de obligacion (null = mensaje general). */
enum class CampoObligacion { TIPO, RESPONSABLE, FECHAS }
data class ErrorAltaObligacion(val mensaje: String, val campo: CampoObligacion? = null)

// FH-010B: traduce los rechazos conocidos del alta de obligacion (el cuerpo de error solo se lee una vez).
internal fun errorAltaObligacion(e: Exception): ErrorAltaObligacion {
    if (e !is HttpException || e.code() !in setOf(400, 404, 409)) return ErrorAltaObligacion(mensajeError(e))
    val d = detalleError(e).orEmpty()
    return when {
        d.startsWith("Ya existe una obligacion activa") ->
            ErrorAltaObligacion("Ya existe una obligación activa de este tipo en esta ubicación.", CampoObligacion.TIPO)
        d.startsWith("El local no tiene una ocupacion vigente") -> ErrorAltaObligacion(
            "El local no tiene una ocupación vigente: el responsable no puede ser el inquilino.", CampoObligacion.RESPONSABLE)
        d.contains("zona comun no puede ser responsabilidad") ->
            ErrorAltaObligacion("En zona común el responsable solo puede ser NHOOD.", CampoObligacion.RESPONSABLE)
        d.startsWith("La proxima fecha") ->
            ErrorAltaObligacion("La próxima fecha no puede ser anterior a la fecha de inicio.", CampoObligacion.FECHAS)
        d.startsWith("El tipo de mantenimiento esta inactivo") ->
            ErrorAltaObligacion("Ese tipo de mantenimiento ya no está activo. Recarga los tipos.", CampoObligacion.TIPO)
        d.startsWith("El centro esta inactivo") -> ErrorAltaObligacion("El centro está inactivo y no admite nuevas obligaciones.")
        d.startsWith("El local esta inactivo") -> ErrorAltaObligacion("El local está inactivo y no admite nuevas obligaciones.")
        d.startsWith("La instalacion") || d.startsWith("El local no pertenece") ->
            ErrorAltaObligacion("La instalación elegida no corresponde a esta ubicación.")
        e.code() == 404 -> ErrorAltaObligacion("El centro, el local, la instalación o el tipo ya no existe. Recarga los datos.")
        e.code() == 400 -> ErrorAltaObligacion("Datos no válidos. Revisa los campos marcados.")
        else -> ErrorAltaObligacion("No se pudo registrar: la operación entra en conflicto con los datos actuales. Recarga los datos.")
    }
}

/** FH-012: campo del formulario de tipos al que se liga un rechazo (null = mensaje general). */
enum class CampoTipo { CODIGO, NOMBRE, PERIODICIDAD, DOCUMENTO }
/** [codigo] = codigo estable del 409 del backend (DATOS_DESACTUALIZADOS, IMPACTO_NO_CONFIRMADO...). */
data class ErrorGestionTipo(val mensaje: String, val campo: CampoTipo? = null, val codigo: String? = null,
                            val obligacionesActivas: Long? = null)

// FH-012: traduce los rechazos de la gestion de tipos. El cuerpo de error solo se lee una vez.
internal fun errorGestionTipo(e: Exception): ErrorGestionTipo {
    if (e !is HttpException || e.code() !in setOf(400, 404, 409)) return ErrorGestionTipo(mensajeError(e))
    val cuerpo = try { e.response()?.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
    return errorGestionTipo(e.code(), cuerpo)
}

internal fun errorGestionTipo(http: Int, cuerpo: String): ErrorGestionTipo {
    val detalle = campoTextoJson(cuerpo, "detail").orEmpty()
    val codigo = campoTextoJson(cuerpo, "codigo")
    val obligaciones = campoNumeroJson(cuerpo, "obligacionesActivas")
    val abiertas = campoNumeroJson(cuerpo, "intervencionesAbiertas")
    return when (codigo) {
        "CODIGO_DUPLICADO" -> ErrorGestionTipo("Ya existe un tipo con ese código.", CampoTipo.CODIGO, codigo)
        "NOMBRE_DUPLICADO" -> ErrorGestionTipo("Ya existe un tipo con ese nombre.", CampoTipo.NOMBRE, codigo)
        "DATOS_DESACTUALIZADOS" -> ErrorGestionTipo(
            "Este tipo ha sido modificado por otro usuario. Actualiza los datos antes de guardar.", null, codigo)
        "IMPACTO_NO_CONFIRMADO" -> ErrorGestionTipo(
            "El tipo se usa en ${obligaciones ?: "varias"} obligaciones activas. Confirma el cambio para guardarlo.",
            null, codigo, obligaciones)
        "INTERVENCIONES_SIN_DOCUMENTO" -> ErrorGestionTipo(
            "No se puede exigir documento: hay ${abiertas ?: "varias"} intervenciones abiertas de este tipo sin evidencias. " +
                "Deben adjuntar una evidencia o resolverse antes del cambio.", CampoTipo.DOCUMENTO, codigo)
        else -> when {
            http == 404 -> ErrorGestionTipo("El tipo de mantenimiento ya no existe. Vuelve atrás y recarga la lista.")
            detalle.contains("codigo de un tipo") -> ErrorGestionTipo("El código no se puede modificar.", CampoTipo.CODIGO)
            detalle.startsWith("El codigo debe") -> ErrorGestionTipo(
                "El código debe tener entre 2 y 40 caracteres: letras, números y guiones, sin espacios.", CampoTipo.CODIGO)
            http == 400 -> ErrorGestionTipo("Datos no válidos. Revisa los campos marcados.")
            else -> ErrorGestionTipo("No se pudo guardar: la operación entra en conflicto con los datos actuales. Recarga los datos.")
        }
    }
}

private fun campoTextoJson(cuerpo: String, campo: String): String? =
    Regex("\"" + campo + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(cuerpo)?.groupValues?.get(1)?.replace("\\\"", "\"")

private fun campoNumeroJson(cuerpo: String, campo: String): Long? =
    Regex("\"" + campo + "\"\\s*:\\s*(\\d+)").find(cuerpo)?.groupValues?.get(1)?.toLongOrNull()

// Extrae "detail" de la respuesta ProblemDetail sin dependencias adicionales.
private fun detalleError(e: HttpException): String? = try {
    val cuerpo = e.response()?.errorBody()?.string().orEmpty()
    Regex("\"detail\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(cuerpo)?.groupValues?.get(1)?.replace("\\\"", "\"")
} catch (_: Exception) {
    null
}

internal val ROLES_GESTION = setOf("ADMIN", "EXPLOTACION")
internal const val ROL_PROVEEDOR = "PROVEEDOR"
internal const val ROL_INQUILINO = "INQUILINO"

const val MAX_BYTES_DOCUMENTO = 20L * 1024 * 1024
val TIPOS_DOCUMENTO = arrayOf("application/pdf", "image/jpeg", "image/png")
private const val MENSAJE_TAMANO_MAXIMO = "El archivo supera el máximo de 20 MB."

internal class DocumentoLocal(val nombre: String, val mime: String, val archivo: File)
private class DocumentoNoValidoException(message: String) : Exception(message)

// FH-006 / P6-C3: la app solo ofrece Iniciar. FINALIZADO ya no es una transicion manual: se alcanza registrando y
// presentando una intervencion, y la validacion decide el cumplimiento (el antiguo boton "Finalizar" daba 409).
fun siguienteEstado(estado: String): String? = when (estado) {
    "PROGRAMADO", "PENDIENTE" -> "EN_PROCESO"
    else -> null
}
