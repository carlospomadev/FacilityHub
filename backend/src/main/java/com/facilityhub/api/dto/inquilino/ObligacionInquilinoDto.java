package com.facilityhub.api.dto.inquilino;

import java.time.LocalDate;
import java.util.List;

// Obligacion de un local con ocupacion vigente del inquilino. proveedorDeclarado solo en las de responsabilidad
// INQUILINO y solo la de su propia ocupacion.
public record ObligacionInquilinoDto(Long id, Long localId, String local, String instalacion, String tipoMantenimiento,
                                     Integer periodicidadMeses, LocalDate proximaFecha, String responsable,
                                     DeclaracionProveedorDto proveedorDeclarado, List<String> accionesPermitidas) {}
