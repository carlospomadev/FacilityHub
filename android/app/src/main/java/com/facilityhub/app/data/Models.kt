package com.facilityhub.app.data

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val tipo: String, val usuarioId: Long, val nombre: String, val email: String, val rol: String)

data class CentroDto(val id: Long, val codigo: String, val nombre: String, val ciudad: String?, val activo: Boolean)
data class LocalDto(val id: Long, val codigo: String, val nombre: String?, val planta: String?, val ubicacion: String?, val activo: Boolean)
data class InquilinoDto(val id: Long, val cif: String?, val razonSocial: String, val nombreComercial: String?, val emailContacto: String?, val telefono: String?)
data class InstalacionDto(val id: Long, val nombre: String, val tipo: String, val descripcion: String?, val estado: String)
// FH-010A / RF-07: instalacion con su ubicacion en el centro (localId nulo = zona comun) y peticion de alta.
data class InstalacionCentroDto(
    val id: Long, val nombre: String, val tipo: String, val descripcion: String?, val estado: String,
    val centroId: Long, val localId: Long?, val localCodigo: String?, val localNombre: String?
)
// FH-010B / RF-09: catalogo de tipos (solo lectura), obligacion con su ubicacion y peticion de alta.
data class TipoMantenimientoDto(
    val id: Long, val codigo: String, val nombre: String, val descripcion: String?, val periodicidadMeses: Int,
    val requiereDocumento: Boolean, val reglaRecalculo: String?
)
data class ObligacionCentroDto(
    val id: Long, val centroId: Long, val localId: Long?, val localCodigo: String?, val localNombre: String?,
    val instalacionId: Long?, val instalacion: String?, val tipoMantenimientoId: Long, val tipoMantenimiento: String,
    val periodicidadMeses: Int, val responsable: String, val fechaInicio: String, val proximaFecha: String,
    val estado: String, val activo: Boolean, val observaciones: String?
)
data class CrearObligacionRequest(
    val localId: Long?, val instalacionId: Long?, val tipoMantenimientoId: Long, val responsable: String,
    val fechaInicio: String, val proximaFecha: String?, val observaciones: String?
)
// FH-012 / RF-08: gestion del catalogo de tipos (huella = valores actuales, para detectar cambios de otro usuario).
data class TipoGestionDto(
    val id: Long, val codigo: String, val nombre: String, val descripcion: String?, val periodicidadMeses: Int,
    val requiereDocumento: Boolean, val reglaRecalculo: String, val activo: Boolean, val obligacionesActivas: Long,
    val huella: String
)
data class CrearTipoRequest(
    val codigo: String, val nombre: String, val descripcion: String?, val periodicidadMeses: Int,
    val requiereDocumento: Boolean, val reglaRecalculo: String
)
data class EditarTipoRequest(
    val nombre: String, val descripcion: String?, val periodicidadMeses: Int, val requiereDocumento: Boolean,
    val reglaRecalculo: String, val huella: String, val confirmarImpacto: Boolean?
)
data class CambiarActivoTipoRequest(val activo: Boolean, val huella: String, val confirmarImpacto: Boolean?)
// FH-013 / RF-03: gestion de centros (activos e inactivos), con su uso y la huella de sus valores.
data class CentroGestionDto(
    val id: Long, val codigo: String, val nombre: String, val direccion: String?, val ciudad: String, val descripcion: String?,
    val activo: Boolean, val localesActivos: Long, val locales: Long, val instalaciones: Long, val obligacionesActivas: Long,
    val ocupacionesVigentes: Long, val ocupacionesFuturas: Long = 0, val mantenimientosAbiertos: Long, val huella: String
)
data class CrearCentroRequest(val codigo: String, val nombre: String, val direccion: String?, val ciudad: String, val descripcion: String?)
data class EditarCentroRequest(val nombre: String, val direccion: String?, val ciudad: String, val descripcion: String?, val huella: String)
data class CambiarActivoCentroRequest(val activo: Boolean, val huella: String, val confirmarImpacto: Boolean?)
// FH-014 / RF-04: gestion de locales (activos e inactivos) con su centro, su uso y la huella de sus valores.
data class LocalGestionDto(
    val id: Long, val centroId: Long, val centroCodigo: String, val centroNombre: String, val centroActivo: Boolean,
    val codigo: String, val nombre: String?, val planta: String?, val ubicacion: String?, val activo: Boolean,
    val instalaciones: Long = 0, val obligacionesActivas: Long = 0, val mantenimientos: Long = 0, val ocupaciones: Long = 0,
    val ocupacionesVigentes: Long = 0, val ocupacionesFuturas: Long = 0, val mantenimientosAbiertos: Long = 0, val huella: String
)
data class CrearLocalRequest(val codigo: String, val nombre: String, val planta: String?, val ubicacion: String?)
// El centro y el codigo no se envian: no se pueden cambiar (D2, D6).
data class EditarLocalRequest(val nombre: String, val planta: String?, val ubicacion: String?, val huella: String)
data class CambiarActivoLocalRequest(val activo: Boolean, val huella: String, val confirmarImpacto: Boolean?)
// FH-015 / RF-05: gestion de inquilinos (activos e inactivos), con su uso y la huella de sus valores.
data class InquilinoGestionDto(
    val id: Long, val cif: String?, val razonSocial: String, val nombreComercial: String?, val emailContacto: String?,
    val telefono: String?, val activo: Boolean, val ocupaciones: Long = 0, val ocupacionesVigentes: Long = 0,
    val ocupacionesFuturas: Long = 0, val mantenimientosAbiertos: Long = 0, val usuarios: Long = 0,
    val solicitudesPendientes: Long = 0, val declaracionesActivas: Long = 0, val huella: String
)
data class CrearInquilinoRequest(val cif: String, val razonSocial: String, val nombreComercial: String?,
                                 val emailContacto: String?, val telefono: String?)
// cif solo se envia si el inquilino no lo tenia (registro antiguo): despues no se puede cambiar (D4).
data class EditarInquilinoRequest(val cif: String?, val razonSocial: String, val nombreComercial: String?,
                                  val emailContacto: String?, val telefono: String?, val huella: String)
data class CambiarActivoInquilinoRequest(val activo: Boolean, val huella: String, val confirmarImpacto: Boolean?)
data class CrearInstalacionRequest(val localId: Long?, val nombre: String, val tipo: String, val descripcion: String?, val estado: String)
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
    val accionesPermitidas: List<String>?,
    // FH-006 / P6-C3: intervencion mas reciente de su empresa y su estado de validacion.
    val intervencionId: Long?,
    val estadoValidacion: String?
)
data class DocumentoDto(val id: Long, val mantenimientoId: Long, val nombreOriginal: String, val tipoDocumento: String?, val fechaDocumento: String?, val fechaCaducidad: String?, val estadoRevision: String, val observaciones: String?, val fechaSubida: String?)
