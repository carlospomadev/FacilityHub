package com.facilityhub.api;

import com.facilityhub.api.dto.CambiarEstadoMantenimientoRequest;
import com.facilityhub.api.dto.MantenimientoDto;
import com.facilityhub.service.MantenimientoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/mantenimientos")
public class MantenimientoController {
    private final MantenimientoService mantenimientoService;

    public MantenimientoController(MantenimientoService mantenimientoService) {
        this.mantenimientoService = mantenimientoService;
    }

    // Identidad del token autenticado; el servicio exige rol de gestion vigente en BD.
    @PatchMapping("/{id}/estado")
    public MantenimientoDto cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody CambiarEstadoMantenimientoRequest request,
                                           Principal principal) {
        return mantenimientoService.cambiarEstado(id, request, principal == null ? null : principal.getName());
    }
}
