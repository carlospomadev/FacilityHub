package com.facilityhub.api.dto.alerta;

import java.time.LocalDateTime;

// FH-006 / P6-D1: alerta propia del usuario autenticado (sin datos del destinatario ni de otros usuarios).
public record AlertaDto(Long id, String tipo, String mensaje, Long mantenimientoId, Long obligacionId, Long intervencionId,
                        LocalDateTime createdAt, LocalDateTime leidaAt) {}
