package com.facilityhub.api.dto.auth;

public record LoginResponse(
    String token,
    String tipo,
    Long usuarioId,
    String nombre,
    String email,
    String rol
) {}
