package com.facilityhub.app.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

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
}
