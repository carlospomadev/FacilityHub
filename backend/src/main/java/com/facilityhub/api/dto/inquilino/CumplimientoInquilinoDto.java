package com.facilityhub.api.dto.inquilino;

import java.time.LocalDate;
import java.util.List;

public record CumplimientoInquilinoDto(Long intervencionId, LocalDate fechaEjecucion, String resultado,
                                       List<CertificadoInquilinoDto> certificados) {}
