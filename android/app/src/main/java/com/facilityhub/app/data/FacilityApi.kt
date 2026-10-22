package com.facilityhub.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}
