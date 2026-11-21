package com.facilityhub.app.data

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface FacilityApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/centros")
    suspend fun centros(): List<CentroDto>

    @GET("api/centros/{id}/locales")
    suspend fun locales(@Path("id") centroId: Long): List<LocalDto>

    @GET("api/locales/{id}/inquilino-actual")
    suspend fun inquilinoActual(@Path("id") localId: Long): InquilinoDto

    @GET("api/locales/{id}/instalaciones")
    suspend fun instalaciones(@Path("id") localId: Long): List<InstalacionDto>

    @GET("api/locales/{id}/obligaciones")
    suspend fun obligaciones(@Path("id") localId: Long): List<ObligacionDto>

    @GET("api/obligaciones/{id}/mantenimientos")
    suspend fun mantenimientos(@Path("id") obligacionId: Long): List<MantenimientoDto>

    @POST("api/obligaciones/{id}/mantenimientos")
    suspend fun crearMantenimiento(@Path("id") obligacionId: Long, @Body request: CrearMantenimientoRequest): MantenimientoDto

    @PATCH("api/mantenimientos/{id}/estado")
    suspend fun cambiarEstadoMantenimiento(@Path("id") mantenimientoId: Long, @Body request: CambiarEstadoMantenimientoRequest): MantenimientoDto

    @GET("api/mantenimientos/{id}/documentos")
    suspend fun documentos(@Path("id") mantenimientoId: Long): List<DocumentoDto>

    @Multipart
    @POST("api/mantenimientos/{id}/documentos")
    suspend fun subirDocumento(@Path("id") mantenimientoId: Long, @Part file: MultipartBody.Part): DocumentoDto

    // FH-006 / P6-B: trabajos del PROVEEDOR autenticado.
    @GET("api/proveedor/trabajos")
    suspend fun trabajos(): List<TrabajoProveedorDto>

    @GET("api/proveedor/trabajos/{id}")
    suspend fun trabajo(@Path("id") mantenimientoId: Long): TrabajoProveedorDto

    // ---------- FH-006 / P6-C3: intervenciones y validacion ----------

    @GET("api/mantenimientos/{id}/intervenciones")
    suspend fun intervenciones(@Path("id") mantenimientoId: Long): List<IntervencionDto>

    @POST("api/mantenimientos/{id}/intervenciones")
    suspend fun registrarIntervencion(@Path("id") mantenimientoId: Long, @Body datos: DatosIntervencionRequest): IntervencionDto

    @GET("api/intervenciones/{id}")
    suspend fun intervencion(@Path("id") id: Long): IntervencionDto

    @PATCH("api/intervenciones/{id}")
    suspend fun actualizarIntervencion(@Path("id") id: Long, @Body datos: DatosIntervencionRequest): IntervencionDto

    @Multipart
    @POST("api/intervenciones/{id}/documentos")
    suspend fun adjuntarDocumento(@Path("id") id: Long, @Part file: MultipartBody.Part, @Query("categoria") categoria: String): IntervencionDto

    @POST("api/intervenciones/{id}/envio")
    suspend fun enviarIntervencion(@Path("id") id: Long, @Body request: EnvioRequest): IntervencionDto

    @POST("api/intervenciones/{id}/validacion")
    suspend fun validarIntervencion(@Path("id") id: Long, @Body request: ValidacionRequest): IntervencionDto

    @GET("api/intervenciones/pendientes")
    suspend fun pendientesValidacion(): List<IntervencionPendienteDto>

    @Streaming
    @GET("api/intervenciones/{id}/documentos/{documentoId}/archivo")
    suspend fun descargarDocumentoIntervencion(@Path("id") id: Long, @Path("documentoId") documentoId: Long): ResponseBody

    @Streaming
    @GET("api/documentos/{id}/archivo")
    suspend fun descargarDocumento(@Path("id") documentoId: Long): ResponseBody

    @GET("api/proveedores")
    suspend fun proveedores(): List<ProveedorDto>

    @GET("api/mantenimientos/{id}/asignacion")
    suspend fun asignacion(@Path("id") mantenimientoId: Long): AsignacionDto

    // Gestion: asignar o reasignar (NHOOD; en obligaciones INQUILINO el backend responde 403/409).
    @PUT("api/mantenimientos/{id}/asignacion")
    suspend fun asignar(@Path("id") mantenimientoId: Long, @Body request: AsignacionRequest): AsignacionDto

    @GET("api/declaraciones-proveedor/pendientes")
    suspend fun declaracionesPendientes(): List<DeclaracionPendienteDto>

    @PATCH("api/declaraciones-proveedor/{id}/revision")
    suspend fun revisarDeclaracion(@Path("id") id: Long, @Body request: RevisionDeclaracionRequest): DeclaracionProveedorDto

    @GET("api/solicitudes-proveedor")
    suspend fun solicitudesGestion(): List<SolicitudProveedorDto>

    @PATCH("api/solicitudes-proveedor/{id}")
    suspend fun resolverSolicitud(@Path("id") id: Long, @Body request: ResolverSolicitudRequest): SolicitudProveedorDto

    // ---------- FH-006 / P6-D1: alertas propias ----------

    @GET("api/alertas")
    suspend fun alertas(@Query("soloNoLeidas") soloNoLeidas: Boolean): List<AlertaDto>

    @GET("api/alertas/no-leidas")
    suspend fun alertasNoLeidas(): NoLeidasDto

    @PATCH("api/alertas/{id}/leida")
    suspend fun marcarAlertaLeida(@Path("id") id: Long): AlertaDto

    // ---------- FH-006 / P6-D3-3: panel de Explotacion (ADMIN y EXPLOTACION) ----------

    @GET("api/gestion/resumen")
    suspend fun resumenGestion(): ResumenGestionDto

    @GET("api/gestion/trabajos")
    suspend fun trabajosGestion(@Query("filtro") filtro: String): List<TrabajoGestionDto>

    // ---------- FH-006 / P6-D3-4: historial del local (mismo endpoint; el backend decide el contenido por rol) ----------

    @GET("api/locales/{id}/historial")
    suspend fun historialGestion(@Path("id") localId: Long): List<HistorialGestionDto>

    @GET("api/locales/{id}/historial")
    suspend fun historialInquilino(@Path("id") localId: Long): List<HistorialInquilinoDto>

    // ---------- FH-006 / P6-C3: area del INQUILINO ----------

    @GET("api/inquilino/obligaciones")
    suspend fun obligacionesInquilino(): List<ObligacionInquilinoDto>

    @GET("api/inquilino/obligaciones/{id}/mantenimientos")
    suspend fun mantenimientosInquilino(@Path("id") obligacionId: Long): List<MantenimientoInquilinoDetalleDto>

    @GET("api/inquilino/proveedores")
    suspend fun buscarProveedor(@Query("cif") cif: String): ProveedorBasicoDto

    @PUT("api/inquilino/obligaciones/{id}/proveedor")
    suspend fun declararProveedor(@Path("id") obligacionId: Long, @Body request: DeclararProveedorRequest): DeclaracionProveedorDto

    @POST("api/inquilino/solicitudes-proveedor")
    suspend fun solicitarProveedor(@Body request: SolicitudProveedorRequest): SolicitudProveedorDto

    @GET("api/inquilino/solicitudes-proveedor")
    suspend fun solicitudesInquilino(): List<SolicitudProveedorDto>

    @PATCH("api/inquilino/mantenimientos/{id}/fecha-prevista")
    suspend fun fijarFechaPrevista(@Path("id") mantenimientoId: Long, @Body request: FechaPrevistaRequest): MantenimientoInquilinoDetalleDto

    @PUT("api/inquilino/mantenimientos/{id}/asignacion")
    suspend fun asignarProveedorAceptado(@Path("id") mantenimientoId: Long, @Body request: AsignacionInquilinoRequest): AsignacionDto
}
