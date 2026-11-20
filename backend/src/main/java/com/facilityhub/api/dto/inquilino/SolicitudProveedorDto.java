package com.facilityhub.api.dto.inquilino;

import java.time.LocalDateTime;

public record SolicitudProveedorDto(Long id, String inquilino, Long obligacionId, String razonSocial, String cif,
                                    String contacto, String observaciones, String estado, Long proveedorId,
                                    String proveedor, String respuesta, LocalDateTime createdAt, LocalDateTime atendidaAt,
                                    long version) {}
