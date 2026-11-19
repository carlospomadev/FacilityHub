package com.facilityhub.api;

import com.facilityhub.api.dto.proveedor.TrabajoProveedorDto;
import com.facilityhub.service.TrabajoProveedorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

// FH-006 / P6-B: trabajos asignados al PROVEEDOR autenticado (solo lectura en esta fase).
@RestController
@RequestMapping("/api/proveedor/trabajos")
public class TrabajoProveedorController {
    private final TrabajoProveedorService trabajoService;

    public TrabajoProveedorController(TrabajoProveedorService trabajoService) {
        this.trabajoService = trabajoService;
    }

    @GetMapping
    public List<TrabajoProveedorDto> listar(Principal principal) {
        return trabajoService.listar(email(principal));
    }

    @GetMapping("/{mantenimientoId}")
    public TrabajoProveedorDto obtener(@PathVariable Long mantenimientoId, Principal principal) {
        return trabajoService.obtener(mantenimientoId, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
