package com.facilityhub.service;

import com.facilityhub.api.dto.proveedor.TrabajoProveedorDto;
import com.facilityhub.domain.AsignacionMantenimiento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.AsignacionMantenimientoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// FH-006 / P6-B: "Mis trabajos" del PROVEEDOR. Solo asignaciones activas de su empresa (activa); el resto,
// incluidos trabajos de otras empresas o inexistentes, responde 404 uniforme. Sin acceso general a centros/locales.
@Service
@Transactional(readOnly = true)
public class TrabajoProveedorService {
    private final AsignacionMantenimientoRepository asignacionRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final PoliticaAccesoService politica;

    public TrabajoProveedorService(AsignacionMantenimientoRepository asignacionRepository,
                                   DocumentoAutorizacionService autorizacion, PoliticaAccesoService politica) {
        this.asignacionRepository = asignacionRepository;
        this.autorizacion = autorizacion;
        this.politica = politica;
    }

    public List<TrabajoProveedorDto> listar(String email) {
        Usuario usuario = proveedorActual(email);
        Optional<Long> proveedorId = autorizacion.proveedorConAcceso(usuario);
        if (proveedorId.isEmpty()) {
            return List.of();
        }
        return asignacionRepository.trabajosActivosDeProveedor(proveedorId.get()).stream()
            .map(a -> toDto(usuario, a))
            .toList();
    }

    public TrabajoProveedorDto obtener(Long mantenimientoId, String email) {
        Usuario usuario = proveedorActual(email);
        Long proveedorId = autorizacion.proveedorConAcceso(usuario).orElseThrow(RecursoNoDisponibleException::new);
        return asignacionRepository.trabajoActivoDeProveedor(proveedorId, mantenimientoId)
            .map(a -> toDto(usuario, a))
            .orElseThrow(RecursoNoDisponibleException::new);
    }

    private Usuario proveedorActual(String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (!autorizacion.esProveedor(usuario)) {
            throw new AccessDeniedException("Operacion no permitida");
        }
        return usuario;
    }

    private TrabajoProveedorDto toDto(Usuario usuario, AsignacionMantenimiento a) {
        Mantenimiento m = a.getMantenimiento();
        ObligacionMantenimiento o = m.getObligacion();
        String local = o.getLocal() == null ? null
            : o.getLocal().getNombre() == null ? o.getLocal().getCodigo()
            : o.getLocal().getCodigo() + " · " + o.getLocal().getNombre();
        return new TrabajoProveedorDto(
            m.getId(),
            m.getFechaProgramada(),
            m.getEstado().name(),
            m.getDescripcion(),
            o.getTipoMantenimiento().getNombre(),
            o.getCentro().getNombre(),
            local,
            o.getInstalacion() == null ? null : o.getInstalacion().getNombre(),
            o.getResponsable().name(),
            AsignacionService.nombreCompleto(a.getTecnico()),
            a.getAsignadoAt(),
            politica.accionesTrabajoProveedor(usuario, a)
        );
    }
}
