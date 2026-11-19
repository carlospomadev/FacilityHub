package com.facilityhub.service;

import com.facilityhub.api.dto.InquilinoDto;
import com.facilityhub.api.dto.InstalacionDto;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.OcupacionLocal;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.InstalacionRepository;
import com.facilityhub.repository.LocalComercialRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class LocalDetalleService {
    private final LocalComercialRepository localRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final InstalacionRepository instalacionRepository;
    private final DocumentoAutorizacionService autorizacion;

    public LocalDetalleService(LocalComercialRepository localRepository,
                               OcupacionLocalRepository ocupacionRepository,
                               InstalacionRepository instalacionRepository,
                               DocumentoAutorizacionService autorizacion) {
        this.localRepository = localRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.instalacionRepository = instalacionRepository;
        this.autorizacion = autorizacion;
    }

    // Se autoriza antes de leer el ocupante. Local inexistente, ajeno o sin ocupante vigente: el mismo 404.
    public InquilinoDto obtenerInquilinoActual(Long localId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (!localRepository.existsById(localId) || !autorizacion.puedeAccederLocal(usuario, localId)) {
            throw new RecursoNoDisponibleException();
        }
        // El indice uq_ocupacion_activa_por_local admite como mucho una ocupacion activa por local;
        // ademas debe estar vigente por fechas (una activa caducada o futura no cuenta).
        LocalDate hoy = autorizacion.hoy();
        Inquilino inquilino = ocupacionRepository.findByLocalIdAndActivoTrue(localId)
            .filter(ocupacion -> esVigente(ocupacion, hoy))
            .orElseThrow(RecursoNoDisponibleException::new)
            .getInquilino();
        // Defensa adicional: un INQUILINO solo puede recibir sus propios datos.
        if (!autorizacion.esGestion(usuario) && !inquilino.getId().equals(usuario.getInquilino().getId())) {
            throw new RecursoNoDisponibleException();
        }
        return new InquilinoDto(
            inquilino.getId(), inquilino.getCif(), inquilino.getRazonSocial(),
            inquilino.getNombreComercial(), inquilino.getEmailContacto(), inquilino.getTelefono()
        );
    }

    // FH-005A2-B: gestion conserva el comportamiento previo; INQUILINO solo con ocupacion propia vigente
    // (misma regla que inquilino-actual). Ajeno o inexistente para INQUILINO: 404 uniforme.
    public List<InstalacionDto> listarInstalaciones(Long localId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (autorizacion.esGestion(usuario)) {
            validarLocal(localId);
        } else if (!localRepository.existsById(localId) || !autorizacion.puedeAccederLocal(usuario, localId)) {
            throw new RecursoNoDisponibleException();
        }
        return instalacionRepository.findByLocalIdOrderByNombreAsc(localId).stream()
            .map(i -> new InstalacionDto(i.getId(), i.getNombre(), i.getTipo(), i.getDescripcion(), i.getEstado().name()))
            .toList();
    }

    // Fecha de fin inclusiva; sin fin = vigente.
    private static boolean esVigente(OcupacionLocal ocupacion, LocalDate hoy) {
        return !ocupacion.getFechaInicio().isAfter(hoy)
            && (ocupacion.getFechaFin() == null || !ocupacion.getFechaFin().isBefore(hoy));
    }

    private void validarLocal(Long localId) {
        if (!localRepository.existsById(localId)) {
            throw new RecursoNoEncontradoException("Local no encontrado: " + localId);
        }
    }
}
