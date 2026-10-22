package com.facilityhub.service;

import com.facilityhub.api.dto.CambiarEstadoMantenimientoRequest;
import com.facilityhub.api.dto.CrearMantenimientoRequest;
import com.facilityhub.api.dto.MantenimientoDto;
import com.facilityhub.domain.EstadoMantenimiento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
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

    public MantenimientoService(MantenimientoRepository mantenimientoRepository,
                                ObligacionMantenimientoRepository obligacionRepository) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.obligacionRepository = obligacionRepository;
    }

    @Transactional(readOnly = true)
    public List<MantenimientoDto> listarPorObligacion(Long obligacionId) {
        if (!obligacionRepository.existsById(obligacionId)) {
            throw new RecursoNoEncontradoException("Obligacion no encontrada: " + obligacionId);
        }
        return mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(obligacionId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public MantenimientoDto crear(Long obligacionId, CrearMantenimientoRequest request) {
        ObligacionMantenimiento obligacion = obligacionRepository.findById(obligacionId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Obligacion no encontrada: " + obligacionId));

        Mantenimiento mantenimiento = new Mantenimiento(obligacion, request.fechaProgramada(), request.descripcion());
        return toDto(mantenimientoRepository.save(mantenimiento));
    }

    @Transactional
    public MantenimientoDto cambiarEstado(Long mantenimientoId, CambiarEstadoMantenimientoRequest request) {
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
}
