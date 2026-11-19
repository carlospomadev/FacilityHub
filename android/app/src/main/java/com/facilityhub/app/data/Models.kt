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
data class CambiarEstadoMantenimientoRequest(val estado: String, val fechaRealizacion: String?, val observaciones: String?)
// FH-006 / P6-B: trabajo asignado al PROVEEDOR (solo lectura; las acciones las decide el backend).
data class TrabajoProveedorDto(
    val mantenimientoId: Long,
    val fechaProgramada: String,
    val estado: String,
    val descripcion: String?,
    val tipoMantenimiento: String,
    val centro: String,
    val local: String?,
    val instalacion: String?,
    val responsable: String,
    val tecnicoAsignado: String?,
    val asignadoAt: String?,
    val accionesPermitidas: List<String>?
)
data class DocumentoDto(val id: Long, val mantenimientoId: Long, val nombreOriginal: String, val tipoDocumento: String?, val fechaDocumento: String?, val fechaCaducidad: String?, val estadoRevision: String, val observaciones: String?, val fechaSubida: String?)
