package com.facilityhub.api.dto.inquilino;

import java.time.LocalDate;
import java.util.List;

// FH-006 / P6-D3-6: historicoP5 = registro P5 sin validacion (validacion_requerida = FALSE); se muestra con su estado
// real y nunca acredita cumplimiento (cumplimiento solo existe con una intervencion APROBADO).
// Sin observaciones internas. cumplimiento: solo intervencion APROBADA y sus certificados VALIDO + COMPARTIDO.
public record MantenimientoInquilinoDetalleDto(Long id, Long obligacionId, LocalDate fechaProgramada,
                                               LocalDate fechaPrevistaInquilino, LocalDate fechaRealizacion, String estado,
                                               String descripcion, String proveedorAsignado,
                                               CumplimientoInquilinoDto cumplimiento, long version,
                                               List<String> accionesPermitidas, boolean historicoP5) {}
