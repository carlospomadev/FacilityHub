package com.facilityhub.api;

import com.facilityhub.api.dto.TipoMantenimientoDto;
import com.facilityhub.service.ObligacionAltaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

// FH-010B / RF-09 (D4): consulta de solo lectura del catalogo de tipos activos, para gestion. Sin alta, edicion ni baja.
@RestController
public class TipoMantenimientoController {
    private final ObligacionAltaService obligacionAltaService;

    public TipoMantenimientoController(ObligacionAltaService obligacionAltaService) {
        this.obligacionAltaService = obligacionAltaService;
    }

    @GetMapping("/api/tipos-mantenimiento")
    public List<TipoMantenimientoDto> listar(Principal principal) {
        return obligacionAltaService.listarTiposActivos(principal == null ? null : principal.getName());
    }
}
