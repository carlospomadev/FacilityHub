package com.facilityhub.api;

import com.facilityhub.api.dto.CambiarEstadoMantenimientoRequest;
import com.facilityhub.api.dto.MantenimientoDto;
import com.facilityhub.service.MantenimientoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mantenimientos")
public class MantenimientoController {
    private final MantenimientoService mantenimientoService;

    public MantenimientoController(MantenimientoService mantenimientoService) {
        this.mantenimientoService = mantenimientoService;
    }

    @PatchMapping("/{id}/estado")
    public MantenimientoDto cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody CambiarEstadoMantenimientoRequest request) {
        return mantenimientoService.cambiarEstado(id, request);
    }
}
