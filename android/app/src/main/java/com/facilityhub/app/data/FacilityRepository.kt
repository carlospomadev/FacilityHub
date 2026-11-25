package com.facilityhub.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class FacilityRepository(private val api: FacilityApi, private val tokenStore: TokenStore) {
    suspend fun login(email: String, password: String): LoginResponse {
        val response = api.login(LoginRequest(email, password))
        tokenStore.token = response.token
        tokenStore.userName = response.nombre
        tokenStore.role = response.rol
        return response
    }

    suspend fun centros() = api.centros()
    suspend fun locales(centroId: Long) = api.locales(centroId)
    suspend fun inquilino(localId: Long) = api.inquilinoActual(localId)
    suspend fun instalaciones(localId: Long) = api.instalaciones(localId)
    suspend fun instalacionesCentro(centroId: Long) = api.instalacionesCentro(centroId)
    suspend fun crearInstalacion(centroId: Long, peticion: CrearInstalacionRequest) = api.crearInstalacion(centroId, peticion)
    suspend fun tiposMantenimiento() = api.tiposMantenimiento()
    suspend fun catalogoTipos() = api.catalogoTipos()
    suspend fun tipo(id: Long) = api.tipo(id)
    suspend fun crearTipo(peticion: CrearTipoRequest) = api.crearTipo(peticion)
    suspend fun editarTipo(id: Long, peticion: EditarTipoRequest) = api.editarTipo(id, peticion)
    suspend fun cambiarActivoTipo(id: Long, peticion: CambiarActivoTipoRequest) = api.cambiarActivoTipo(id, peticion)
    suspend fun obligacionesCentro(centroId: Long) = api.obligacionesCentro(centroId)
    suspend fun crearObligacion(centroId: Long, peticion: CrearObligacionRequest) = api.crearObligacion(centroId, peticion)
    suspend fun obligaciones(localId: Long) = api.obligaciones(localId)
    suspend fun mantenimientos(obligacionId: Long) = api.mantenimientos(obligacionId)
    suspend fun crearMantenimiento(obligacionId: Long, fecha: String, descripcion: String?) =
        api.crearMantenimiento(obligacionId, CrearMantenimientoRequest(fecha, descripcion))
    suspend fun cambiarEstadoMantenimiento(mantenimientoId: Long, estado: String, fechaRealizacion: String?, observaciones: String?) =
        api.cambiarEstadoMantenimiento(mantenimientoId, CambiarEstadoMantenimientoRequest(estado, fechaRealizacion, observaciones))

    suspend fun documentos(mantenimientoId: Long) = api.documentos(mantenimientoId)
    suspend fun trabajos() = api.trabajos()
    suspend fun trabajo(mantenimientoId: Long) = api.trabajo(mantenimientoId)
    // El cuerpo se envia en streaming desde el archivo; el backend espera la parte multipart "file".
    suspend fun subirDocumento(mantenimientoId: Long, nombre: String, mime: String, archivo: File) =
        api.subirDocumento(mantenimientoId, MultipartBody.Part.createFormData("file", nombre, archivo.asRequestBody(mime.toMediaType())))

    // ---------- FH-006 / P6-C3 ----------
    suspend fun intervenciones(mantenimientoId: Long) = api.intervenciones(mantenimientoId)
    suspend fun registrarIntervencion(mantenimientoId: Long, datos: DatosIntervencionRequest) = api.registrarIntervencion(mantenimientoId, datos)
    suspend fun intervencion(id: Long) = api.intervencion(id)
    suspend fun actualizarIntervencion(id: Long, datos: DatosIntervencionRequest) = api.actualizarIntervencion(id, datos)
    suspend fun adjuntarDocumento(id: Long, categoria: String, nombre: String, mime: String, archivo: File) =
        api.adjuntarDocumento(id, MultipartBody.Part.createFormData("file", nombre, archivo.asRequestBody(mime.toMediaType())), categoria)
    suspend fun enviarIntervencion(id: Long, version: Long) = api.enviarIntervencion(id, EnvioRequest(version))
    suspend fun validarIntervencion(id: Long, request: ValidacionRequest) = api.validarIntervencion(id, request)
    suspend fun pendientesValidacion() = api.pendientesValidacion()
    suspend fun descargarDocumentoIntervencion(id: Long, documentoId: Long) = api.descargarDocumentoIntervencion(id, documentoId)
    suspend fun descargarDocumento(documentoId: Long) = api.descargarDocumento(documentoId)
    suspend fun proveedores() = api.proveedores()
    suspend fun asignacion(mantenimientoId: Long) = api.asignacion(mantenimientoId)
    suspend fun asignar(mantenimientoId: Long, proveedorId: Long) = api.asignar(mantenimientoId, AsignacionRequest(proveedorId, null))
    suspend fun declaracionesPendientes() = api.declaracionesPendientes()
    suspend fun revisarDeclaracion(id: Long, request: RevisionDeclaracionRequest) = api.revisarDeclaracion(id, request)
    suspend fun solicitudesGestion() = api.solicitudesGestion()
    suspend fun resolverSolicitud(id: Long, request: ResolverSolicitudRequest) = api.resolverSolicitud(id, request)
    suspend fun obligacionesInquilino() = api.obligacionesInquilino()
    suspend fun mantenimientosInquilino(obligacionId: Long) = api.mantenimientosInquilino(obligacionId)
    suspend fun buscarProveedor(cif: String) = api.buscarProveedor(cif)
    suspend fun declararProveedor(obligacionId: Long, proveedorId: Long) = api.declararProveedor(obligacionId, DeclararProveedorRequest(proveedorId))
    suspend fun solicitarProveedor(request: SolicitudProveedorRequest) = api.solicitarProveedor(request)
    suspend fun solicitudesInquilino() = api.solicitudesInquilino()
    suspend fun fijarFechaPrevista(mantenimientoId: Long, fecha: String, version: Long) =
        api.fijarFechaPrevista(mantenimientoId, FechaPrevistaRequest(fecha, version))
    suspend fun asignarProveedorAceptado(mantenimientoId: Long) = api.asignarProveedorAceptado(mantenimientoId, AsignacionInquilinoRequest(null))

    // ---------- FH-006 / P6-D1 ----------
    suspend fun alertas(soloNoLeidas: Boolean) = api.alertas(soloNoLeidas)
    suspend fun alertasNoLeidas() = api.alertasNoLeidas().noLeidas
    suspend fun marcarAlertaLeida(id: Long) = api.marcarAlertaLeida(id)

    // ---------- FH-006 / P6-D3-3 ----------
    suspend fun resumenGestion() = api.resumenGestion()
    suspend fun trabajosGestion(filtro: String) = api.trabajosGestion(filtro)

    // ---------- FH-006 / P6-D3-4 ----------
    suspend fun historialGestion(localId: Long) = api.historialGestion(localId)
    suspend fun historialInquilino(localId: Long) = api.historialInquilino(localId)
}
