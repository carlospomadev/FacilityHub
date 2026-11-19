package com.facilityhub.api;

import com.facilityhub.api.dto.CentroDto;
import com.facilityhub.api.dto.LocalDto;
import com.facilityhub.service.CentroService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

// La identidad sale del token autenticado (subject = email), nunca de parametros del cliente.
@RestController
@RequestMapping("/api/centros")
public class CentroController {
    private final CentroService centroService;

    public CentroController(CentroService centroService) { this.centroService = centroService; }

    @GetMapping
    public List<CentroDto> listar(Principal principal) {
        return centroService.listarCentros(email(principal));
    }

    @GetMapping("/{id}")
    public CentroDto obtener(@PathVariable Long id, Principal principal) {
        return centroService.obtenerCentro(id, email(principal));
    }

    @GetMapping("/{id}/locales")
    public List<LocalDto> listarLocales(@PathVariable Long id, Principal principal) {
        return centroService.listarLocales(id, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
