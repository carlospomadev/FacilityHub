package com.facilityhub.app.data

// FH-006 / P6-C3: modelos del circuito de intervenciones, validacion y area del inquilino.
// Las acciones visibles salen siempre de accionesPermitidas (calculadas por el backend).

data class DocumentoIntervencionDto(
    val id: Long,
    val nombreOriginal: String,
    val categoria: String?,
    val estadoRevision: String,
    val visibilidad: String,
    val fechaSubida: String?
)

data class ValidacionDto(val numeroEnvio: Int, val accion: String, val comentario: String?, val validadaPor: String?, val fecha: String?)

data class IntervencionDto(
    val id: Long,
    val mantenimientoId: Long,
    val proveedorId: Long,
    val proveedor: String?,
    val tecnico: String?,
    val origen: String,
    val registradaPor: String?,
    val registradaAt: String?,
    val numeroEnvio: Int,
    val fechaEjecucion: String?,
    val actuaciones: String?,
    val resultado: String?,
    val incidencias: String?,
    val estadoValidacion: String,
    val ejecucionAnticipada: Boolean,
    val enviadaAt: String?,
    val enviadaPor: String?,
    val version: Long,
    val documentos: List<DocumentoIntervencionDto>?,
    val validaciones: List<ValidacionDto>?,
    val accionesPermitidas: List<String>?
)

data class IntervencionPendienteDto(
    val id: Long,
    val mantenimientoId: Long,
    val tipoMantenimiento: String,
    val centro: String?,
    val local: String?,
    val responsable: String,
    val proveedor: String?,
    val origen: String,
    val numeroEnvio: Int,
    val fechaEjecucion: String?,
    val resultado: String?,
    val ejecucionAnticipada: Boolean,
    val enviadaAt: String?
)

// Campos nulos = sin cambio (alta sin version; edicion con version obligatoria).
data class DatosIntervencionRequest(
    val version: Long?,
    val fechaEjecucion: String?,
    val actuaciones: String?,
    val resultado: String?,
    val incidencias: String?
)

data class EnvioRequest(val version: Long)

data class ValidacionRequest(val accion: String, val comentario: String?, val version: Long, val documentosCompartidos: List<Long>?)

data class TecnicoDto(val id: Long, val nombre: String?, val apellidos: String?, val email: String?, val activo: Boolean)

data class ProveedorDto(
    val id: Long,
    val cif: String?,
    val razonSocial: String,
    val emailContacto: String?,
    val telefono: String?,
    val activo: Boolean,
    val tecnicos: List<TecnicoDto>?
)

data class AsignacionDto(
    val id: Long,
    val mantenimientoId: Long,
    val proveedorId: Long,
    val proveedor: String,
    val tecnicoUsuarioId: Long?,
    val tecnico: String?,
    val asignadoAt: String?,
    val activa: Boolean
)

// ---------- INQUILINO (P6-C2) ----------

data class DeclaracionProveedorDto(
    val id: Long,
    val obligacionId: Long,
    val proveedorId: Long,
    val proveedor: String,
    val estado: String,
    val motivoRevision: String?,
    val declaradoAt: String?,
    val revisadoAt: String?,
    val activo: Boolean,
    val version: Long
)

data class ObligacionInquilinoDto(
    val id: Long,
    val localId: Long,
    val local: String,
    val instalacion: String?,
    val tipoMantenimiento: String,
    val periodicidadMeses: Int?,
    val proximaFecha: String,
    val responsable: String,
    val proveedorDeclarado: DeclaracionProveedorDto?,
    val accionesPermitidas: List<String>?
)

data class CertificadoInquilinoDto(val id: Long, val nombre: String, val fechaSubida: String?)

data class CumplimientoInquilinoDto(
    val intervencionId: Long,
    val fechaEjecucion: String?,
    val resultado: String?,
    val certificados: List<CertificadoInquilinoDto>?
)

data class MantenimientoInquilinoDetalleDto(
    val id: Long,
    val obligacionId: Long,
    val fechaProgramada: String,
    val fechaPrevistaInquilino: String?,
    val fechaRealizacion: String?,
    val estado: String,
    val descripcion: String?,
    val proveedorAsignado: String?,
    val cumplimiento: CumplimientoInquilinoDto?,
    val version: Long,
    val accionesPermitidas: List<String>?
)

data class ProveedorBasicoDto(val id: Long, val razonSocial: String)
data class DeclararProveedorRequest(val proveedorId: Long)
data class FechaPrevistaRequest(val fechaPrevista: String, val version: Long)
data class AsignacionInquilinoRequest(val tecnicoUsuarioId: Long?)

data class SolicitudProveedorRequest(
    val razonSocial: String,
    val cif: String?,
    val contacto: String?,
    val observaciones: String?,
    val obligacionId: Long?
)

data class SolicitudProveedorDto(
    val id: Long,
    val inquilino: String?,
    val obligacionId: Long?,
    val razonSocial: String,
    val cif: String?,
    val contacto: String?,
    val observaciones: String?,
    val estado: String,
    val proveedorId: Long?,
    val proveedor: String?,
    val respuesta: String?,
    val createdAt: String?,
    val atendidaAt: String?,
    val version: Long
)

data class ResolverSolicitudRequest(val accion: String, val proveedorId: Long?, val respuesta: String?, val version: Long)

data class DeclaracionPendienteDto(
    val id: Long,
    val obligacionId: Long,
    val tipoMantenimiento: String,
    val local: String,
    val inquilino: String,
    val proveedorId: Long,
    val proveedor: String,
    val cif: String?,
    val declaradoAt: String?,
    val version: Long
)

data class RevisionDeclaracionRequest(val accion: String, val motivo: String?, val version: Long)
