package com.facilityhub.api;

import com.facilityhub.api.dto.gestion.ResumenGestionDto;
import com.facilityhub.api.dto.gestion.TrabajoGestionDto;
import com.facilityhub.service.PanelGestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

// FH-006 / P6-D3-1: panel de gestion (solo lectura). ADMIN y EXPLOTACION; el servicio relee el rol en BD.
@RestController
@RequestMapping("/api/gestion")
public class GestionController {
    private final PanelGestionService panelService;

    public GestionController(PanelGestionService panelService) {
        this.panelService = panelService;
    }

    @GetMapping("/resumen")
    public ResumenGestionDto resumen(Principal principal) {
        return panelService.resumen(email(principal));
    }

    @GetMapping("/trabajos")
    public List<TrabajoGestionDto> trabajos(@RequestParam(required = false) String filtro, Principal principal) {
        return panelService.trabajos(filtro, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
