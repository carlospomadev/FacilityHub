package com.facilityhub.api;

import com.facilityhub.api.dto.alerta.ResultadoRecordatoriosDto;
import com.facilityhub.service.RecordatorioService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

// FH-006 / P6-D2: ejecucion manual y controlada de recordatorios (solo ADMIN). Por defecto es una simulacion.
@RestController
@RequestMapping("/api/admin/recordatorios")
public class RecordatorioController {
    private final RecordatorioService recordatorioService;

    public RecordatorioController(RecordatorioService recordatorioService) {
        this.recordatorioService = recordatorioService;
    }

    @PostMapping("/ejecutar")
    public ResultadoRecordatoriosDto ejecutar(@RequestParam(defaultValue = "true") boolean simulacion, Principal principal) {
        return recordatorioService.ejecutarManual(simulacion, principal == null ? null : principal.getName());
    }
}
