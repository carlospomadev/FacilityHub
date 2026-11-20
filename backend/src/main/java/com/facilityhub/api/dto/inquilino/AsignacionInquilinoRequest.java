package com.facilityhub.api.dto.inquilino;

// El proveedor es siempre el ACEPTADO de la obligacion; el inquilino solo puede indicar un tecnico de esa empresa.
public record AsignacionInquilinoRequest(Long tecnicoUsuarioId) {}
