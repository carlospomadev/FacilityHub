package com.facilityhub.api.dto.gestion;

import java.time.LocalDate;

// FH-006 / P6-D3: contadores del panel de gestion. Cada contador es el tamano del listado de su filtro en
// GET /api/gestion/trabajos (mismo calculo), salvo declaraciones y solicitudes, que son los de sus bandejas.
public record ResumenGestionDto(
    LocalDate fecha,
    int preavisoDias,
    int vencidos,
    int proximos,
    int pendientesValidacion,
    int requierenCorreccion,
    int sinAsignar,
    int declaracionesPendientes,
    int solicitudesPendientes
) {}
