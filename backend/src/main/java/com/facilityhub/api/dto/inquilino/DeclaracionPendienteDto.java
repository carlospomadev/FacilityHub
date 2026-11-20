package com.facilityhub.api.dto.inquilino;

import java.time.LocalDateTime;

// Bandeja de gestion: declaraciones PROPUESTO activas.
public record DeclaracionPendienteDto(Long id, Long obligacionId, String tipoMantenimiento, String local, String inquilino,
                                      Long proveedorId, String proveedor, String cif, LocalDateTime declaradoAt,
                                      long version) {}
