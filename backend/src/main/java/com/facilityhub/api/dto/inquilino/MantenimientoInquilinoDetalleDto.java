package com.facilityhub.api.dto.inquilino;

import java.time.LocalDate;
import java.util.List;

// Sin observaciones internas. cumplimiento: solo intervencion APROBADA y sus certificados VALIDO + COMPARTIDO.
public record MantenimientoInquilinoDetalleDto(Long id, Long obligacionId, LocalDate fechaProgramada,
                                               LocalDate fechaPrevistaInquilino, LocalDate fechaRealizacion, String estado,
                                               String descripcion, String proveedorAsignado,
                                               CumplimientoInquilinoDto cumplimiento, long version,
                                               List<String> accionesPermitidas) {}
