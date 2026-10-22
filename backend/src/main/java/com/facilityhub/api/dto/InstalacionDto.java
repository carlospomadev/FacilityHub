package com.facilityhub.api.dto;

public record InstalacionDto(
    Long id,
    String nombre,
    String tipo,
    String descripcion,
    String estado
) {}
