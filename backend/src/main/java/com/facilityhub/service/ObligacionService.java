package com.facilityhub.service;

import com.facilityhub.api.dto.ObligacionDto;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.Usuario;
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
    private final DocumentoAutorizacionService autorizacion;

    public ObligacionService(LocalComercialRepository localRepository, ObligacionMantenimientoRepository obligacionRepository,
                             DocumentoAutorizacionService autorizacion) {
        this.localRepository = localRepository;
        this.obligacionRepository = obligacionRepository;
        this.autorizacion = autorizacion;
    }

    // FH-005A2-C: gestion conserva el comportamiento previo; INQUILINO solo con acceso vigente al local
    // (puedeAccederLocal: ocupacion propia vigente, local y centro activos). Ajeno, inactivo o inexistente:
    // 404 uniforme, comprobado antes de consultar obligaciones.
    public List<ObligacionDto> listarPorLocal(Long localId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (autorizacion.esGestion(usuario)) {
            if (!localRepository.existsById(localId)) {
                throw new RecursoNoEncontradoException("Local no encontrado: " + localId);
            }
        } else if (!localRepository.existsById(localId) || !autorizacion.puedeAccederLocal(usuario, localId)) {
            throw new RecursoNoDisponibleException();
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
