package com.facilityhub.app.data

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val tipo: String, val usuarioId: Long, val nombre: String, val email: String, val rol: String)

data class CentroDto(val id: Long, val codigo: String, val nombre: String, val ciudad: String?, val activo: Boolean)
data class LocalDto(val id: Long, val codigo: String, val nombre: String?, val planta: String?, val ubicacion: String?, val activo: Boolean)
data class InquilinoDto(val id: Long, val cif: String?, val razonSocial: String, val nombreComercial: String?, val emailContacto: String?, val telefono: String?)
data class InstalacionDto(val id: Long, val nombre: String, val tipo: String, val descripcion: String?, val estado: String)
data class ObligacionDto(val id: Long, val localId: Long, val instalacion: String?, val tipoMantenimiento: String, val periodicidadMeses: Int, val proximaFecha: String, val estado: String)
data class MantenimientoDto(val id: Long, val obligacionId: Long, val fechaProgramada: String, val fechaRealizacion: String?, val estado: String, val descripcion: String?, val observaciones: String?)
data class CrearMantenimientoRequest(val fechaProgramada: String, val descripcion: String?)
