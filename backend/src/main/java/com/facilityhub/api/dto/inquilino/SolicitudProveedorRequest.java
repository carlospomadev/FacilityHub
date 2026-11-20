package com.facilityhub.api.dto.inquilino;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitudProveedorRequest(@NotBlank @Size(max = 150) String razonSocial, @Size(max = 20) String cif,
                                        @Size(max = 300) String contacto, @Size(max = 2000) String observaciones,
                                        Long obligacionId) {}
