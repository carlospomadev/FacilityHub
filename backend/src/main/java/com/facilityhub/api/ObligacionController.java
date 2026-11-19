package com.facilityhub.api;

import com.facilityhub.api.dto.CambiarResponsableRequest;
import com.facilityhub.api.dto.CrearMantenimientoRequest;
import com.facilityhub.api.dto.MantenimientoDto;
import com.facilityhub.api.dto.ObligacionDto;
import com.facilityhub.service.MantenimientoService;
import com.facilityhub.service.ObligacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

// La identidad sale del token autenticado (subject = email), nunca de parametros del cliente.
@RestController
public class ObligacionController {
    private final ObligacionService obligacionService;
    private final MantenimientoService mantenimientoService;

    public ObligacionController(ObligacionService obligacionService, MantenimientoService mantenimientoService) {
        this.obligacionService = obligacionService;
        this.mantenimientoService = mantenimientoService;
    }

    @GetMapping("/api/locales/{localId}/obligaciones")
    public List<ObligacionDto> listarPorLocal(@PathVariable Long localId, Principal principal) {
        return obligacionService.listarPorLocal(localId, email(principal));
    }

    // Gestion recibe MantenimientoDto; INQUILINO, MantenimientoInquilinoDto (sin observaciones).
    @GetMapping("/api/obligaciones/{obligacionId}/mantenimientos")
    public List<?> listarMantenimientos(@PathVariable Long obligacionId, Principal principal) {
        return mantenimientoService.listarPorObligacion(obligacionId, email(principal));
    }

    @PostMapping("/api/obligaciones/{obligacionId}/mantenimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public MantenimientoDto crearMantenimiento(@PathVariable Long obligacionId,
                                                @Valid @RequestBody CrearMantenimientoRequest request,
                                                Principal principal) {
        return mantenimientoService.crear(obligacionId, request, email(principal));
    }

    // FH-006 / P6-B: cambio del responsable contractual (ADMIN y EXPLOTACION).
    @PatchMapping("/api/obligaciones/{obligacionId}/responsable")
    public ObligacionDto cambiarResponsable(@PathVariable Long obligacionId,
                                            @Valid @RequestBody CambiarResponsableRequest request,
                                            Principal principal) {
        return obligacionService.cambiarResponsable(obligacionId, request.responsable(), email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
