package com.facilityhub.api;

import com.facilityhub.api.dto.proveedor.AsignacionDto;
import com.facilityhub.api.dto.proveedor.AsignacionRequest;
import com.facilityhub.service.AsignacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

// FH-006 / P6-B: asignacion de proveedor a un mantenimiento (gestion; EXPLOTACION solo en obligaciones NHOOD).
@RestController
@RequestMapping("/api/mantenimientos/{mantenimientoId}/asignacion")
public class AsignacionController {
    private final AsignacionService asignacionService;

    public AsignacionController(AsignacionService asignacionService) {
        this.asignacionService = asignacionService;
    }

    @GetMapping
    public AsignacionDto consultar(@PathVariable Long mantenimientoId, Principal principal) {
        return asignacionService.consultar(mantenimientoId, email(principal));
    }

    @PutMapping
    public AsignacionDto asignar(@PathVariable Long mantenimientoId, @Valid @RequestBody AsignacionRequest request,
                                 Principal principal) {
        return asignacionService.asignar(mantenimientoId, request, email(principal));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirar(@PathVariable Long mantenimientoId, Principal principal) {
        asignacionService.retirar(mantenimientoId, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
