package com.facilityhub.api;

import com.facilityhub.api.dto.InquilinoDto;
import com.facilityhub.api.dto.InstalacionDto;
import com.facilityhub.service.LocalDetalleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locales")
public class LocalController {
    private final LocalDetalleService localDetalleService;

    public LocalController(LocalDetalleService localDetalleService) {
        this.localDetalleService = localDetalleService;
    }

    @GetMapping("/{id}/inquilino-actual")
    public InquilinoDto inquilinoActual(@PathVariable Long id) {
        return localDetalleService.obtenerInquilinoActual(id);
    }

    @GetMapping("/{id}/instalaciones")
    public List<InstalacionDto> instalaciones(@PathVariable Long id) {
        return localDetalleService.listarInstalaciones(id);
    }
}
