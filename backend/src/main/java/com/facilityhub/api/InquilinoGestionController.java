package com.facilityhub.api;

import com.facilityhub.api.dto.inquilino.gestion.CambiarActivoInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.CrearInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.EditarInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.InquilinoGestionDto;
import com.facilityhub.service.InquilinoGestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

// FH-015 / RF-05: gestion de inquilinos (/api/inquilinos). Distinto del area del rol INQUILINO (/api/inquilino/**).
// Permisos (D1): consulta ADMIN y EXPLOTACION; escritura solo ADMIN (SecurityConfig y, de nuevo, el servicio).
@RestController
@RequestMapping("/api/inquilinos")
public class InquilinoGestionController {
    private final InquilinoGestionService service;

    public InquilinoGestionController(InquilinoGestionService service) {
        this.service = service;
    }

    @GetMapping("/gestion")
    public List<InquilinoGestionDto> listar(Principal principal) {
        return service.listar(email(principal));
    }

    @GetMapping("/gestion/{id}")
    public InquilinoGestionDto obtener(@PathVariable Long id, Principal principal) {
        return service.obtener(id, email(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InquilinoGestionDto crear(@Valid @RequestBody CrearInquilinoRequest request, Principal principal) {
        return service.crear(request, email(principal));
    }

    @PatchMapping("/{id}")
    public InquilinoGestionDto editar(@PathVariable Long id, @Valid @RequestBody EditarInquilinoRequest request,
                                      Principal principal) {
        return service.editar(id, request, email(principal));
    }

    @PatchMapping("/{id}/activo")
    public InquilinoGestionDto cambiarActivo(@PathVariable Long id, @Valid @RequestBody CambiarActivoInquilinoRequest request,
                                             Principal principal) {
        return service.cambiarActivo(id, request, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
