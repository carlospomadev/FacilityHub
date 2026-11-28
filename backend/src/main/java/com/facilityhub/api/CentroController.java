package com.facilityhub.api;

import com.facilityhub.api.dto.CentroDto;
import com.facilityhub.api.dto.CrearInstalacionRequest;
import com.facilityhub.api.dto.CrearObligacionRequest;
import com.facilityhub.api.dto.InstalacionCentroDto;
import com.facilityhub.api.dto.LocalDto;
import com.facilityhub.api.dto.ObligacionCentroDto;
import com.facilityhub.api.dto.centro.CambiarActivoCentroRequest;
import com.facilityhub.api.dto.centro.CentroGestionDto;
import com.facilityhub.api.dto.centro.CrearCentroRequest;
import com.facilityhub.api.dto.centro.EditarCentroRequest;
import com.facilityhub.service.CentroGestionService;
import com.facilityhub.service.CentroService;
import com.facilityhub.service.InstalacionService;
import com.facilityhub.service.ObligacionAltaService;
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
    private final ObligacionAltaService obligacionAltaService;
    private final CentroGestionService centroGestionService;

    public CentroController(CentroService centroService, InstalacionService instalacionService,
                            ObligacionAltaService obligacionAltaService, CentroGestionService centroGestionService) {
        this.centroService = centroService;
        this.instalacionService = instalacionService;
        this.obligacionAltaService = obligacionAltaService;
        this.centroGestionService = centroGestionService;
    }

    // FH-013 / RF-03 (D1, D8): gestion de centros. Consulta (activos e inactivos) para ADMIN y EXPLOTACION.
    @GetMapping("/gestion")
    public List<CentroGestionDto> listarGestion(Principal principal) {
        return centroGestionService.listar(email(principal));
    }

    @GetMapping("/gestion/{id}")
    public CentroGestionDto obtenerGestion(@PathVariable Long id, Principal principal) {
        return centroGestionService.obtener(id, email(principal));
    }

    // FH-013 / RF-03: alta, edicion y desactivacion/reactivacion, solo ADMIN.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CentroGestionDto crear(@Valid @RequestBody CrearCentroRequest request, Principal principal) {
        return centroGestionService.crear(request, email(principal));
    }

    @PatchMapping("/{id}")
    public CentroGestionDto editar(@PathVariable Long id, @Valid @RequestBody EditarCentroRequest request,
                                   Principal principal) {
        return centroGestionService.editar(id, request, email(principal));
    }

    @PatchMapping("/{id}/activo")
    public CentroGestionDto cambiarActivo(@PathVariable Long id, @Valid @RequestBody CambiarActivoCentroRequest request,
                                          Principal principal) {
        return centroGestionService.cambiarActivo(id, request, email(principal));
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

    // FH-010B / RF-09: obligaciones activas del centro (zona comun y locales), solo gestion.
    @GetMapping("/{id}/obligaciones")
    public List<ObligacionCentroDto> listarObligaciones(@PathVariable Long id, Principal principal) {
        return obligacionAltaService.listarDelCentro(id, email(principal));
    }

    // FH-010B / RF-09: alta de una obligacion (solo la obligacion; ningun mantenimiento automatico). 201.
    @PostMapping("/{id}/obligaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public ObligacionCentroDto crearObligacion(@PathVariable Long id, @Valid @RequestBody CrearObligacionRequest request,
                                               Principal principal) {
        return obligacionAltaService.crear(id, request, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
