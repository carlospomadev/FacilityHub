package com.facilityhub.api.dto;

public record InquilinoDto(
    Long id,
    String cif,
    String razonSocial,
    String nombreComercial,
    String emailContacto,
    String telefono
) {}
