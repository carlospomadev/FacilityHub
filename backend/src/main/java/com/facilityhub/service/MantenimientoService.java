package com.facilityhub.service;

import com.facilityhub.api.dto.CambiarEstadoMantenimientoRequest;
import com.facilityhub.api.dto.CrearMantenimientoRequest;
import com.facilityhub.api.dto.MantenimientoDto;
import com.facilityhub.api.dto.MantenimientoInquilinoDto;
import com.facilityhub.domain.EstadoMantenimiento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.domain.EstadoValidacion;
import com.facilityhub.repository.IntervencionRepository;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.ObligacionMantenimientoRepository;
import java.util.EnumSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class MantenimientoService {
    private final MantenimientoRepository mantenimientoRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final IntervencionRepository intervencionRepository;

    public MantenimientoService(MantenimientoRepository mantenimientoRepository,
                                ObligacionMantenimientoRepository obligacionRepository,
                                DocumentoAutorizacionService autorizacion,
                                IntervencionRepository intervencionRepository) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.obligacionRepository = obligacionRepository;
        this.autorizacion = autorizacion;
        this.intervencionRepository = intervencionRepository;
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
        // FH-006 / P6-C2: solo los mantenimientos cubiertos por su ocupacion (sin historial de ocupantes anteriores).
        return mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(obligacionId).stream()
            .filter(m -> autorizacion.puedeLeerMantenimiento(usuario, m))
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

    // FH-006 / P6-C1: este endpoint P5 ya no puede eludir la validacion documental:
    // - nunca pone FINALIZADO (se hace presentando una intervencion) ni recalcula la proxima fecha (solo al aprobar);
    // - no modifica mantenimientos FINALIZADO o CANCELADO ni los que tienen una intervencion abierta.
    // Transiciones admitidas (gestion): PROGRAMADO/PENDIENTE/EN_PROCESO entre si y hacia CANCELADO.
    @Transactional
    public MantenimientoDto cambiarEstado(Long mantenimientoId, CambiarEstadoMantenimientoRequest request, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        Mantenimiento mantenimiento = mantenimientoRepository.findById(mantenimientoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Mantenimiento no encontrado: " + mantenimientoId));
        if (request.estado() == EstadoMantenimiento.FINALIZADO) {
            throw new ConflictoException("Para finalizar, registra y presenta una intervencion; la validacion decide el cumplimiento");
        }
        if (mantenimiento.getEstado() == EstadoMantenimiento.FINALIZADO || mantenimiento.getEstado() == EstadoMantenimiento.CANCELADO) {
            throw new ConflictoException("El mantenimiento no admite cambios de estado en " + mantenimiento.getEstado());
        }
        if (intervencionRepository.existsByMantenimientoIdAndEstadoValidacionIn(mantenimientoId,
            EnumSet.of(EstadoValidacion.BORRADOR, EstadoValidacion.PENDIENTE_VALIDACION, EstadoValidacion.REQUIERE_CORRECCION))) {
            throw new ConflictoException("El mantenimiento tiene una intervencion en curso");
        }
        mantenimiento.cambiarEstado(request.estado(), null, request.observaciones());
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
