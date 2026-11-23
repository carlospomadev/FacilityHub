package com.facilityhub.api;

import com.facilityhub.api.dto.CentroDto;
import com.facilityhub.api.dto.CrearInstalacionRequest;
import com.facilityhub.api.dto.InstalacionCentroDto;
import com.facilityhub.api.dto.LocalDto;
import com.facilityhub.service.CentroService;
import com.facilityhub.service.InstalacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

// La identidad sale del token autenticado (subject = email), nunca de parametros del cliente.
@RestController
@RequestMapping("/api/centros")
public class CentroController {
    private final CentroService centroService;
    private final InstalacionService instalacionService;

    public CentroController(CentroService centroService, InstalacionService instalacionService) {
        this.centroService = centroService;
        this.instalacionService = instalacionService;
    }

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

    // FH-010A / RF-07: instalaciones del centro (zona comun y locales), solo gestion.
    @GetMapping("/{id}/instalaciones")
    public List<InstalacionCentroDto> listarInstalaciones(@PathVariable Long id, Principal principal) {
        return instalacionService.listarDelCentro(id, email(principal));
    }

    // FH-010A / RF-07: alta de una instalacion; localId nulo = zona comun. 201 con la instalacion persistida.
    @PostMapping("/{id}/instalaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public InstalacionCentroDto crearInstalacion(@PathVariable Long id, @Valid @RequestBody CrearInstalacionRequest request,
                                                 Principal principal) {
        return instalacionService.crear(id, request, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
