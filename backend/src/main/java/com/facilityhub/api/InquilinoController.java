package com.facilityhub.api;

import com.facilityhub.api.dto.inquilino.AsignacionInquilinoRequest;
import com.facilityhub.api.dto.inquilino.DeclaracionProveedorDto;
import com.facilityhub.api.dto.inquilino.DeclararProveedorRequest;
import com.facilityhub.api.dto.inquilino.FechaPrevistaRequest;
import com.facilityhub.api.dto.inquilino.MantenimientoInquilinoDetalleDto;
import com.facilityhub.api.dto.inquilino.ObligacionInquilinoDto;
import com.facilityhub.api.dto.inquilino.ProveedorBasicoDto;
import com.facilityhub.api.dto.inquilino.SolicitudProveedorDto;
import com.facilityhub.api.dto.inquilino.SolicitudProveedorRequest;
import com.facilityhub.api.dto.proveedor.AsignacionDto;
import com.facilityhub.service.DeclaracionProveedorService;
import com.facilityhub.service.InquilinoService;
import com.facilityhub.service.SolicitudProveedorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

// FH-006 / P6-C2: area del INQUILINO (rol exigido por URL; acceso por ocupacion y responsabilidad en los servicios).
@RestController
@RequestMapping("/api/inquilino")
public class InquilinoController {
    private final InquilinoService inquilinoService;
    private final DeclaracionProveedorService declaracionService;
    private final SolicitudProveedorService solicitudService;

    public InquilinoController(InquilinoService inquilinoService, DeclaracionProveedorService declaracionService,
                               SolicitudProveedorService solicitudService) {
        this.inquilinoService = inquilinoService;
        this.declaracionService = declaracionService;
        this.solicitudService = solicitudService;
    }

    @GetMapping("/obligaciones")
    public List<ObligacionInquilinoDto> obligaciones(Principal principal) {
        return inquilinoService.obligaciones(email(principal));
    }

    @GetMapping("/obligaciones/{obligacionId}/mantenimientos")
    public List<MantenimientoInquilinoDetalleDto> mantenimientos(@PathVariable Long obligacionId, Principal principal) {
        return inquilinoService.mantenimientos(obligacionId, email(principal));
    }

    @GetMapping("/proveedores")
    public ProveedorBasicoDto buscarProveedor(@RequestParam String cif, Principal principal) {
        return inquilinoService.buscarProveedor(cif, email(principal));
    }

    @PutMapping("/obligaciones/{obligacionId}/proveedor")
    public DeclaracionProveedorDto declarar(@PathVariable Long obligacionId, @Valid @RequestBody DeclararProveedorRequest request,
                                           Principal principal) {
        return declaracionService.declarar(obligacionId, request.proveedorId(), email(principal));
    }

    @PostMapping("/solicitudes-proveedor")
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudProveedorDto solicitar(@Valid @RequestBody SolicitudProveedorRequest request, Principal principal) {
        return solicitudService.crear(request, email(principal));
    }

    @GetMapping("/solicitudes-proveedor")
    public List<SolicitudProveedorDto> solicitudes(Principal principal) {
        return solicitudService.propias(email(principal));
    }

    @PatchMapping("/mantenimientos/{mantenimientoId}/fecha-prevista")
    public MantenimientoInquilinoDetalleDto fechaPrevista(@PathVariable Long mantenimientoId,
                                                          @Valid @RequestBody FechaPrevistaRequest request, Principal principal) {
        return inquilinoService.fijarFechaPrevista(mantenimientoId, request, email(principal));
    }

    @PutMapping("/mantenimientos/{mantenimientoId}/asignacion")
    public AsignacionDto asignar(@PathVariable Long mantenimientoId,
                                 @RequestBody(required = false) AsignacionInquilinoRequest request, Principal principal) {
        return inquilinoService.asignarProveedorAceptado(mantenimientoId, request == null ? null : request.tecnicoUsuarioId(),
            email(principal));
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
