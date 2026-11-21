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

data class AsignacionRequest(val proveedorId: Long, val tecnicoUsuarioId: Long?)

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

// ---------- Alertas internas (P6-D1) ----------

// Alerta propia del usuario autenticado. Los identificadores solo sirven para navegar: la pantalla de destino vuelve a
// pedir el recurso al backend, que comprueba los permisos (una alerta no concede acceso).
data class AlertaDto(
    val id: Long,
    val tipo: String,
    val mensaje: String,
    val mantenimientoId: Long?,
    val obligacionId: Long?,
    val intervencionId: Long?,
    val createdAt: String?,
    val leidaAt: String?
)

data class NoLeidasDto(val noLeidas: Long)

// ---------- FH-006 / P6-D3-3: panel de Explotacion (solo lectura) ----------

// Contadores calculados por el backend (mismos listados que /api/gestion/trabajos).
data class ResumenGestionDto(
    val fecha: String?,
    val preavisoDias: Int,
    val vencidos: Int,
    val proximos: Int,
    val pendientesValidacion: Int,
    val requierenCorreccion: Int,
    val sinAsignar: Int,
    val declaracionesPendientes: Int,
    val solicitudesPendientes: Int
)

// diasHastaVencimiento lo calcula el backend (negativo = dias de retraso); Android solo lo presenta.
data class TrabajoGestionDto(
    val mantenimientoId: Long,
    val intervencionId: Long?,
    val tipoMantenimiento: String,
    val centro: String?,
    val local: String?,
    val zonaComun: Boolean,
    val responsable: String?,
    val fechaProgramada: String?,
    val fechaRealizacion: String?,
    val estado: String?,
    val estadoIntervencion: String?,
    val proveedorAsignado: String?,
    val diasHastaVencimiento: Long
)

// ---------- FH-006 / P6-D3-4: historial del local (GET /api/locales/{id}/historial) ----------

// Gestion (ADMIN, EXPLOTACION). tipoRegistro: APROBADO | CERRADO_SIN_CUMPLIMIENTO | HISTORICO_P5. Un HISTORICO_P5 no
// tiene intervencion ni estadoValidacion: nunca se presenta como aprobado.
data class HistorialGestionDto(
    val tipoRegistro: String,
    val etiqueta: String,
    val mantenimientoId: Long,
    val obligacionId: Long,
    val tipoMantenimiento: String,
    val instalacion: String?,
    val responsable: String?,
    val fechaProgramada: String?,
    val fechaRealizacion: String?,
    val estadoMantenimiento: String?,
    val observaciones: String?,
    val intervencionId: Long?,
    val fechaEjecucion: String?,
    val resultado: String?,
    val estadoValidacion: String?,
    val proveedor: String?,
    val documentos: List<DocumentoIntervencionDto>?
)

// INQUILINO: solo cumplimientos aprobados de su ocupacion vigente y certificados VALIDO + COMPARTIDO.
data class HistorialInquilinoDto(
    val mantenimientoId: Long,
    val obligacionId: Long,
    val tipoMantenimiento: String,
    val instalacion: String?,
    val fechaProgramada: String?,
    val fechaRealizacion: String?,
    val cumplimiento: CumplimientoInquilinoDto?
)
