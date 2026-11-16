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
    suspend fun obligaciones(localId: Long) = api.obligaciones(localId)
    suspend fun mantenimientos(obligacionId: Long) = api.mantenimientos(obligacionId)
    suspend fun crearMantenimiento(obligacionId: Long, fecha: String, descripcion: String?) =
        api.crearMantenimiento(obligacionId, CrearMantenimientoRequest(fecha, descripcion))
    suspend fun cambiarEstadoMantenimiento(mantenimientoId: Long, estado: String, fechaRealizacion: String?, observaciones: String?) =
        api.cambiarEstadoMantenimiento(mantenimientoId, CambiarEstadoMantenimientoRequest(estado, fechaRealizacion, observaciones))

    suspend fun documentos(mantenimientoId: Long) = api.documentos(mantenimientoId)
    // El cuerpo se envia en streaming desde el archivo; el backend espera la parte multipart "file".
    suspend fun subirDocumento(mantenimientoId: Long, nombre: String, mime: String, archivo: File) =
        api.subirDocumento(mantenimientoId, MultipartBody.Part.createFormData("file", nombre, archivo.asRequestBody(mime.toMediaType())))
}
