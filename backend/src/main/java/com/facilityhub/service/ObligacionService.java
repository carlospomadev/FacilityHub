package com.facilityhub.service;

import com.facilityhub.api.dto.ObligacionDto;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.repository.LocalComercialRepository;
import com.facilityhub.repository.ObligacionMantenimientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ObligacionService {
    private final LocalComercialRepository localRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;

    public ObligacionService(LocalComercialRepository localRepository, ObligacionMantenimientoRepository obligacionRepository) {
        this.localRepository = localRepository;
        this.obligacionRepository = obligacionRepository;
    }

    public List<ObligacionDto> listarPorLocal(Long localId) {
        if (!localRepository.existsById(localId)) {
            throw new RecursoNoEncontradoException("Local no encontrado: " + localId);
        }
        return obligacionRepository.findByLocalIdAndActivoTrueOrderByProximaFechaAsc(localId).stream()
            .map(this::toDto)
            .toList();
    }

    private ObligacionDto toDto(ObligacionMantenimiento o) {
        return new ObligacionDto(
            o.getId(),
            o.getLocal().getId(),
            o.getInstalacion() != null ? o.getInstalacion().getNombre() : null,
            o.getTipoMantenimiento().getNombre(),
            o.getTipoMantenimiento().getPeriodicidadMeses(),
            o.getProximaFecha(),
            o.getEstado().name()
        );
    }
}
