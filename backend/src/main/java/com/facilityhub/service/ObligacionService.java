package com.facilityhub.service;

import com.facilityhub.api.dto.ObligacionDto;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.ResponsableObligacion;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.LocalComercialRepository;
import com.facilityhub.repository.ObligacionMantenimientoRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ObligacionService {
    private final LocalComercialRepository localRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final DocumentoAutorizacionService autorizacion;

    public ObligacionService(LocalComercialRepository localRepository, ObligacionMantenimientoRepository obligacionRepository,
                             OcupacionLocalRepository ocupacionRepository, DocumentoAutorizacionService autorizacion) {
        this.localRepository = localRepository;
        this.obligacionRepository = obligacionRepository;
        this.ocupacionRepository = ocupacionRepository;
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

    // FH-006 / P6-B: cambio del responsable contractual (ADMIN y EXPLOTACION). INQUILINO exige local con ocupacion
    // vigente; una obligacion de zona comun solo puede ser NHOOD (tambien lo impide un CHECK en BD).
    @Transactional
    public ObligacionDto cambiarResponsable(Long obligacionId, ResponsableObligacion responsable, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        ObligacionMantenimiento obligacion = obligacionRepository.findById(obligacionId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Obligacion no encontrada: " + obligacionId));
        if (responsable == ResponsableObligacion.INQUILINO) {
            if (obligacion.getLocal() == null) {
                throw new IllegalArgumentException("Una obligacion de zona comun no puede ser responsabilidad del inquilino");
            }
            if (!ocupacionRepository.existeOcupacionVigenteDelLocal(obligacion.getLocal().getId(), autorizacion.hoy())) {
                throw new ConflictoException("El local no tiene una ocupacion vigente");
            }
        }
        obligacion.cambiarResponsable(responsable);
        return toDto(obligacion);
    }

    private ObligacionDto toDto(ObligacionMantenimiento o) {
        return new ObligacionDto(
            o.getId(),
            o.getLocal() != null ? o.getLocal().getId() : null,
            o.getInstalacion() != null ? o.getInstalacion().getNombre() : null,
            o.getTipoMantenimiento().getNombre(),
            o.getTipoMantenimiento().getPeriodicidadMeses(),
            o.getProximaFecha(),
            o.getEstado().name(),
            o.getCentro() != null ? o.getCentro().getId() : null,
            o.getResponsable() != null ? o.getResponsable().name() : null
        );
    }
}
