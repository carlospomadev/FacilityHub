package com.facilityhub.api;

import com.facilityhub.api.dto.CrearMantenimientoRequest;
import com.facilityhub.api.dto.MantenimientoDto;
import com.facilityhub.api.dto.ObligacionDto;
import com.facilityhub.service.MantenimientoService;
import com.facilityhub.service.ObligacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ObligacionController {
    private final ObligacionService obligacionService;
    private final MantenimientoService mantenimientoService;

    public ObligacionController(ObligacionService obligacionService, MantenimientoService mantenimientoService) {
        this.obligacionService = obligacionService;
        this.mantenimientoService = mantenimientoService;
    }

    @GetMapping("/api/locales/{localId}/obligaciones")
    public List<ObligacionDto> listarPorLocal(@PathVariable Long localId) {
        return obligacionService.listarPorLocal(localId);
    }

    @GetMapping("/api/obligaciones/{obligacionId}/mantenimientos")
    public List<MantenimientoDto> listarMantenimientos(@PathVariable Long obligacionId) {
        return mantenimientoService.listarPorObligacion(obligacionId);
    }

    @PostMapping("/api/obligaciones/{obligacionId}/mantenimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public MantenimientoDto crearMantenimiento(@PathVariable Long obligacionId,
                                                @Valid @RequestBody CrearMantenimientoRequest request) {
        return mantenimientoService.crear(obligacionId, request);
    }
}
