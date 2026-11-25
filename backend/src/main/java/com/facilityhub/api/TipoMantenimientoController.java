package com.facilityhub.api;

import com.facilityhub.api.dto.TipoMantenimientoDto;
import com.facilityhub.api.dto.tipo.CambiarActivoTipoRequest;
import com.facilityhub.api.dto.tipo.CrearTipoRequest;
import com.facilityhub.api.dto.tipo.EditarTipoRequest;
import com.facilityhub.api.dto.tipo.TipoGestionDto;
import com.facilityhub.service.ObligacionAltaService;
import com.facilityhub.service.TipoMantenimientoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

// FH-010B / RF-09 (D4): GET de tipos ACTIVOS para el formulario de obligaciones (contrato sin cambios).
// FH-012 / RF-08: gestion del catalogo. Consulta: ADMIN y EXPLOTACION. Alta, edicion y activacion: solo ADMIN.
@RestController
@RequestMapping("/api/tipos-mantenimiento")
public class TipoMantenimientoController {
    private final ObligacionAltaService obligacionAltaService;
    private final TipoMantenimientoService tipoService;

    public TipoMantenimientoController(ObligacionAltaService obligacionAltaService, TipoMantenimientoService tipoService) {
        this.obligacionAltaService = obligacionAltaService;
        this.tipoService = tipoService;
    }

    @GetMapping
    public List<TipoMantenimientoDto> listar(Principal principal) {
        return obligacionAltaService.listarTiposActivos(email(principal));
    }

    // Catalogo completo (activos e inactivos) con el numero de obligaciones activas y la huella de cada tipo.
    @GetMapping("/catalogo")
    public List<TipoGestionDto> catalogo(Principal principal) {
        return tipoService.listarCatalogo(email(principal));
    }

    @GetMapping("/{id}")
    public TipoGestionDto obtener(@PathVariable Long id, Principal principal) {
        return tipoService.obtener(id, email(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TipoGestionDto crear(@Valid @RequestBody CrearTipoRequest request, Principal principal) {
        return tipoService.crear(request, email(principal));
    }

    @PatchMapping("/{id}")
    public TipoGestionDto editar(@PathVariable Long id, @Valid @RequestBody EditarTipoRequest request, Principal principal) {
        return tipoService.editar(id, request, email(principal));
    }

    @PatchMapping("/{id}/activo")
    public TipoGestionDto cambiarActivo(@PathVariable Long id, @Valid @RequestBody CambiarActivoTipoRequest request,
                                        Principal principal) {
        return tipoService.cambiarActivo(id, request, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
