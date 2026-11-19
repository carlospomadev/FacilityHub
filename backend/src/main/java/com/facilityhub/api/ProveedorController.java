package com.facilityhub.api;

import com.facilityhub.api.dto.proveedor.ActualizarProveedorRequest;
import com.facilityhub.api.dto.proveedor.CambiarActivoRequest;
import com.facilityhub.api.dto.proveedor.CrearProveedorRequest;
import com.facilityhub.api.dto.proveedor.CrearTecnicoRequest;
import com.facilityhub.api.dto.proveedor.ProveedorDto;
import com.facilityhub.api.dto.proveedor.TecnicoDto;
import com.facilityhub.service.ProveedorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

// FH-006 / P6-B: consulta para ADMIN y EXPLOTACION; altas, cambios y bajas solo ADMIN (URL + servicio, D-1).
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {
    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public List<ProveedorDto> listar(Principal principal) {
        return proveedorService.listar(email(principal));
    }

    @GetMapping("/{id}")
    public ProveedorDto obtener(@PathVariable Long id, Principal principal) {
        return proveedorService.obtener(id, email(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProveedorDto crear(@Valid @RequestBody CrearProveedorRequest request, Principal principal) {
        return proveedorService.crear(request, email(principal));
    }

    @PatchMapping("/{id}")
    public ProveedorDto actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarProveedorRequest request,
                                   Principal principal) {
        return proveedorService.actualizar(id, request, email(principal));
    }

    @PostMapping("/{id}/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public TecnicoDto crearTecnico(@PathVariable Long id, @Valid @RequestBody CrearTecnicoRequest request,
                                   Principal principal) {
        return proveedorService.crearTecnico(id, request, email(principal));
    }

    @PatchMapping("/{id}/usuarios/{usuarioId}")
    public TecnicoDto cambiarActivoTecnico(@PathVariable Long id, @PathVariable Long usuarioId,
                                           @Valid @RequestBody CambiarActivoRequest request, Principal principal) {
        return proveedorService.cambiarActivoTecnico(id, usuarioId, request, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
