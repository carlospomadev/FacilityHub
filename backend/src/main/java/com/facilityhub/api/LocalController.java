package com.facilityhub.api;

import com.facilityhub.api.dto.InquilinoDto;
import com.facilityhub.api.dto.InstalacionDto;
import com.facilityhub.service.LocalDetalleService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/locales")
public class LocalController {
    private final LocalDetalleService localDetalleService;

    public LocalController(LocalDetalleService localDetalleService) {
        this.localDetalleService = localDetalleService;
    }

    // La identidad sale del token autenticado (subject = email), nunca de parametros del cliente.
    @GetMapping("/{id}/inquilino-actual")
    public InquilinoDto inquilinoActual(@PathVariable Long id, Principal principal) {
        return localDetalleService.obtenerInquilinoActual(id, principal == null ? null : principal.getName());
    }

    @GetMapping("/{id}/instalaciones")
    public List<InstalacionDto> instalaciones(@PathVariable Long id, Principal principal) {
        return localDetalleService.listarInstalaciones(id, principal == null ? null : principal.getName());
    }
}
