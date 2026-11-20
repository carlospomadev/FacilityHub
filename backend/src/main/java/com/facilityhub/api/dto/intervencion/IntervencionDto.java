package com.facilityhub.api.dto.intervencion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// FH-006 / P6-C1: intervencion para gestion y para el proveedor autor. Sin observaciones internas del mantenimiento.
// Registra quien ejecuta (proveedor/tecnico), quien registra, quien presenta y, en validaciones, quien valida.
public record IntervencionDto(
    Long id,
    Long mantenimientoId,
    Long proveedorId,
    String proveedor,
    String tecnico,
    String origen,
    String registradaPor,
    LocalDateTime registradaAt, // cuando se introdujo en FacilityHub; fechaEjecucion es la fecha real de la visita

    int numeroEnvio,
    LocalDate fechaEjecucion,
    String actuaciones,
    String resultado,
    String incidencias,
    String estadoValidacion,
    boolean ejecucionAnticipada,
    LocalDateTime enviadaAt,
    String enviadaPor,
    long version,
    List<DocumentoIntervencionDto> documentos,
    List<ValidacionDto> validaciones,
    List<String> accionesPermitidas
) {}
