package com.facilityhub.api.dto.documento;

// Respuesta de listado: completa para gestion, minima para inquilinos.
public sealed interface DocumentoRespuesta permits DocumentoDto, DocumentoInquilinoDto {
}
