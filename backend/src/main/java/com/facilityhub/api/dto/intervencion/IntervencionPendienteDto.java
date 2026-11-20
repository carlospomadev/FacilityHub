package com.facilityhub.api.dto.intervencion;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Fila de la bandeja de validacion de gestion.
public record IntervencionPendienteDto(
    Long id,
    Long mantenimientoId,
    String tipoMantenimiento,
    String centro,
    String local,
    String responsable,
    String proveedor,
    String origen,
    int numeroEnvio,
    LocalDate fechaEjecucion,
    String resultado,
    boolean ejecucionAnticipada,
    LocalDateTime enviadaAt
) {}
