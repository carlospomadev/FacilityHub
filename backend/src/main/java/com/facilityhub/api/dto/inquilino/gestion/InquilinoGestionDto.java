package com.facilityhub.api.dto.inquilino.gestion;

// FH-015 / RF-05: inquilino para la gestion (activos e inactivos), con su uso y la huella de sus valores (D7).
// usuarios, solicitudesPendientes y declaracionesActivas son informativos (resumen de la desactivacion).
public record InquilinoGestionDto(
    Long id,
    String cif,
    String razonSocial,
    String nombreComercial,
    String emailContacto,
    String telefono,
    boolean activo,
    long ocupaciones,
    long ocupacionesVigentes,
    long ocupacionesFuturas,
    long mantenimientosAbiertos,
    long usuarios,
    long solicitudesPendientes,
    long declaracionesActivas,
    String huella
) {}
