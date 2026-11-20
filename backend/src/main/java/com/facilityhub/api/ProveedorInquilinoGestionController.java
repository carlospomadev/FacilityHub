package com.facilityhub.api;

import com.facilityhub.api.dto.inquilino.DeclaracionPendienteDto;
import com.facilityhub.api.dto.inquilino.DeclaracionProveedorDto;
import com.facilityhub.api.dto.inquilino.ResolverSolicitudRequest;
import com.facilityhub.api.dto.inquilino.RevisionDeclaracionRequest;
import com.facilityhub.api.dto.inquilino.SolicitudProveedorDto;
import com.facilityhub.domain.EstadoSolicitudProveedor;
import com.facilityhub.service.DeclaracionProveedorService;
import com.facilityhub.service.SolicitudProveedorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

// FH-006 / P6-C2: gestion de proveedores declarados y solicitados por inquilinos. Revision de declaraciones: ADMIN y
// EXPLOTACION. Solicitudes: consulta ADMIN y EXPLOTACION; resolucion solo ADMIN (alta de empresas, D-1).
@RestController
public class ProveedorInquilinoGestionController {
    private final DeclaracionProveedorService declaracionService;
    private final SolicitudProveedorService solicitudService;

    public ProveedorInquilinoGestionController(DeclaracionProveedorService declaracionService,
                                               SolicitudProveedorService solicitudService) {
        this.declaracionService = declaracionService;
        this.solicitudService = solicitudService;
    }

    @GetMapping("/api/declaraciones-proveedor/pendientes")
    public List<DeclaracionPendienteDto> pendientes(Principal principal) {
        return declaracionService.pendientes(email(principal));
    }

    @PatchMapping("/api/declaraciones-proveedor/{id}/revision")
    public DeclaracionProveedorDto revisar(@PathVariable Long id, @Valid @RequestBody RevisionDeclaracionRequest request,
                                           Principal principal) {
        return declaracionService.revisar(id, request, email(principal));
    }

    @GetMapping("/api/solicitudes-proveedor")
    public List<SolicitudProveedorDto> solicitudes(@RequestParam(required = false) EstadoSolicitudProveedor estado,
                                                   Principal principal) {
        return solicitudService.listar(estado, email(principal));
    }

    @PatchMapping("/api/solicitudes-proveedor/{id}")
    public SolicitudProveedorDto resolver(@PathVariable Long id, @Valid @RequestBody ResolverSolicitudRequest request,
                                          Principal principal) {
        return solicitudService.resolver(id, request, email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
