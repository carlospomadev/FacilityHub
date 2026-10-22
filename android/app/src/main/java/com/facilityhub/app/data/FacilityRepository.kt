package com.facilityhub.app.data

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
}
