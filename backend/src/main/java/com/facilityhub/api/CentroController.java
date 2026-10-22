package com.facilityhub.api;

import com.facilityhub.api.dto.CentroDto;
import com.facilityhub.api.dto.LocalDto;
import com.facilityhub.service.CentroService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/centros")
public class CentroController {
    private final CentroService centroService;

    public CentroController(CentroService centroService) { this.centroService = centroService; }

    @GetMapping
    public List<CentroDto> listar() { return centroService.listarCentrosActivos(); }

    @GetMapping("/{id}")
    public CentroDto obtener(@PathVariable Long id) { return centroService.obtenerCentro(id); }

    @GetMapping("/{id}/locales")
    public List<LocalDto> listarLocales(@PathVariable Long id) { return centroService.listarLocales(id); }
}
