package com.facilityhub.api;

import com.facilityhub.api.dto.InquilinoDto;
import com.facilityhub.api.dto.InstalacionDto;
import com.facilityhub.service.HistorialService;
import com.facilityhub.service.LocalDetalleService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/locales")
public class LocalController {
    private final LocalDetalleService localDetalleService;
    private final HistorialService historialService;

    public LocalController(LocalDetalleService localDetalleService, HistorialService historialService) {
        this.localDetalleService = localDetalleService;
        this.historialService = historialService;
    }

    // La identidad sale del token autenticado (subject = email), nunca de parametros del cliente.
    @GetMapping("/{id}/inquilino-actual")
    public InquilinoDto inquilinoActual(@PathVariable Long id, Principal principal) {
        return localDetalleService.obtenerInquilinoActual(id, principal == null ? null : principal.getName());
    }

    // FH-006 / P6-D3-2: historial consolidado del local (gestion: completo; INQUILINO: aprobados de su ocupacion vigente).
    @GetMapping("/{id}/historial")
    public List<?> historial(@PathVariable Long id, Principal principal) {
        return historialService.historial(id, principal == null ? null : principal.getName());
    }

    @GetMapping("/{id}/instalaciones")
    public List<InstalacionDto> instalaciones(@PathVariable Long id, Principal principal) {
        return localDetalleService.listarInstalaciones(id, principal == null ? null : principal.getName());
    }
}
