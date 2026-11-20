package com.facilityhub.api.dto.inquilino;

import java.time.LocalDateTime;

public record DeclaracionProveedorDto(Long id, Long obligacionId, Long proveedorId, String proveedor, String estado,
                                      String motivoRevision, LocalDateTime declaradoAt, LocalDateTime revisadoAt,
                                      boolean activo, long version) {}
