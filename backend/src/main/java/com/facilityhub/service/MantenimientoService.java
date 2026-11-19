package com.facilityhub.service;

import com.facilityhub.api.dto.CambiarEstadoMantenimientoRequest;
import com.facilityhub.api.dto.CrearMantenimientoRequest;
import com.facilityhub.api.dto.MantenimientoDto;
import com.facilityhub.api.dto.MantenimientoInquilinoDto;
import com.facilityhub.domain.EstadoMantenimiento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.ObligacionMantenimientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class MantenimientoService {
    private final MantenimientoRepository mantenimientoRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final DocumentoAutorizacionService autorizacion;

    public MantenimientoService(MantenimientoRepository mantenimientoRepository,
                                ObligacionMantenimientoRepository obligacionRepository,
                                DocumentoAutorizacionService autorizacion) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.obligacionRepository = obligacionRepository;
        this.autorizacion = autorizacion;
    }

    // FH-005A2-C: gestion -> MantenimientoDto completo (comportamiento previo). INQUILINO -> solo si la obligacion
    // esta activa y su local es accesible (puedeAccederLocal), con MantenimientoInquilinoDto sin observaciones (D1).
    // Ajena, inactiva o inexistente: 404 uniforme antes de leer mantenimientos.
    @Transactional(readOnly = true)
    public List<?> listarPorObligacion(Long obligacionId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (autorizacion.esGestion(usuario)) {
            if (!obligacionRepository.existsById(obligacionId)) {
                throw new RecursoNoEncontradoException("Obligacion no encontrada: " + obligacionId);
            }
            return mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(obligacionId).stream()
                .map(this::toDto)
                .toList();
        }
        ObligacionMantenimiento obligacion = obligacionRepository.findById(obligacionId)
            .filter(ObligacionMantenimiento::isActivo)
            .orElseThrow(RecursoNoDisponibleException::new);
        // FH-006: las obligaciones de zona comun (sin local) no son visibles para el inquilino.
        if (obligacion.getLocal() == null || !autorizacion.puedeAccederLocal(usuario, obligacion.getLocal().getId())) {
            throw new RecursoNoDisponibleException();
        }
        return mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(obligacionId).stream()
            .map(this::toInquilinoDto)
            .toList();
    }

    // Escrituras: ademas del filtro de URL (rol del JWT), rol vigente en BD antes de escribir (403 si no es gestion).
    @Transactional
    public MantenimientoDto crear(Long obligacionId, CrearMantenimientoRequest request, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        ObligacionMantenimiento obligacion = obligacionRepository.findById(obligacionId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Obligacion no encontrada: " + obligacionId));
        Mantenimiento mantenimiento = new Mantenimiento(obligacion, request.fechaProgramada(), request.descripcion());
        return toDto(mantenimientoRepository.save(mantenimiento));
    }

    @Transactional
    public MantenimientoDto cambiarEstado(Long mantenimientoId, CambiarEstadoMantenimientoRequest request, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        Mantenimiento mantenimiento = mantenimientoRepository.findById(mantenimientoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Mantenimiento no encontrado: " + mantenimientoId));
        mantenimiento.cambiarEstado(request.estado(), request.fechaRealizacion(), request.observaciones());
        if (request.estado() == EstadoMantenimiento.FINALIZADO) {
            ObligacionMantenimiento obligacion = mantenimiento.getObligacion();
            LocalDate siguiente = request.fechaRealizacion().plusMonths(obligacion.getTipoMantenimiento().getPeriodicidadMeses());
            obligacion.actualizarProximaFecha(siguiente);
        }
        return toDto(mantenimiento);
    }

    private MantenimientoDto toDto(Mantenimiento m) {
        return new MantenimientoDto(
            m.getId(),
            m.getObligacion().getId(),
            m.getFechaProgramada(),
            m.getFechaRealizacion(),
            m.getEstado().name(),
            m.getDescripcion(),
            m.getObservaciones()
        );
    }

    private MantenimientoInquilinoDto toInquilinoDto(Mantenimiento m) {
        return new MantenimientoInquilinoDto(
            m.getId(),
            m.getObligacion().getId(),
            m.getFechaProgramada(),
            m.getFechaRealizacion(),
            m.getEstado().name(),
            m.getDescripcion()
        );
    }
}
