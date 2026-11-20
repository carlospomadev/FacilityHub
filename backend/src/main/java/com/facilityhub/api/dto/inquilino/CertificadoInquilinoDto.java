package com.facilityhub.api.dto.inquilino;

import java.time.LocalDateTime;

// Descarga por GET /api/documentos/{id}/archivo (misma regla de acceso).
public record CertificadoInquilinoDto(Long id, String nombre, LocalDateTime fechaSubida) {}
