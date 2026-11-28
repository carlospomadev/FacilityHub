package com.facilityhub.api;

import com.facilityhub.api.dto.InquilinoDto;
import com.facilityhub.api.dto.InstalacionDto;
import com.facilityhub.api.dto.local.CambiarActivoLocalRequest;
import com.facilityhub.api.dto.local.EditarLocalRequest;
import com.facilityhub.api.dto.local.LocalGestionDto;
import com.facilityhub.service.HistorialService;
import com.facilityhub.service.LocalDetalleService;
import com.facilityhub.service.LocalGestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/locales")
public class LocalController {
    private final LocalDetalleService localDetalleService;
    private final HistorialService historialService;
    private final LocalGestionService localGestionService;

    public LocalController(LocalDetalleService localDetalleService, HistorialService historialService,
                           LocalGestionService localGestionService) {
        this.localDetalleService = localDetalleService;
        this.historialService = historialService;
        this.localGestionService = localGestionService;
    }

    // FH-014 / RF-04 (D1): detalle de gestion (activo o inactivo, con su uso), ADMIN y EXPLOTACION.
    @GetMapping("/gestion/{id}")
    public LocalGestionDto obtenerGestion(@PathVariable Long id, Principal principal) {
        return localGestionService.obtener(id, email(principal));
    }

    // FH-014 / RF-04: edicion (no cambia centro ni codigo) y desactivacion/reactivacion, solo ADMIN.
    @PatchMapping("/{id}")
    public LocalGestionDto editar(@PathVariable Long id, @Valid @RequestBody EditarLocalRequest request, Principal principal) {
        return localGestionService.editar(id, request, email(principal));
    }

    @PatchMapping("/{id}/activo")
    public LocalGestionDto cambiarActivo(@PathVariable Long id, @Valid @RequestBody CambiarActivoLocalRequest request,
                                         Principal principal) {
        return localGestionService.cambiarActivo(id, request, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
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
