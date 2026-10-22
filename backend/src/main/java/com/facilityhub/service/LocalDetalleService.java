package com.facilityhub.service;

import com.facilityhub.api.dto.InquilinoDto;
import com.facilityhub.api.dto.InstalacionDto;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.repository.InstalacionRepository;
import com.facilityhub.repository.LocalComercialRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LocalDetalleService {
    private final LocalComercialRepository localRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final InstalacionRepository instalacionRepository;

    public LocalDetalleService(LocalComercialRepository localRepository,
                               OcupacionLocalRepository ocupacionRepository,
                               InstalacionRepository instalacionRepository) {
        this.localRepository = localRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.instalacionRepository = instalacionRepository;
    }

    public InquilinoDto obtenerInquilinoActual(Long localId) {
        validarLocal(localId);
        Inquilino inquilino = ocupacionRepository.findByLocalIdAndActivoTrue(localId)
            .orElseThrow(() -> new RecursoNoEncontradoException("El local no tiene una ocupacion activa"))
            .getInquilino();
        return new InquilinoDto(
            inquilino.getId(), inquilino.getCif(), inquilino.getRazonSocial(),
            inquilino.getNombreComercial(), inquilino.getEmailContacto(), inquilino.getTelefono()
        );
    }

    public List<InstalacionDto> listarInstalaciones(Long localId) {
        validarLocal(localId);
        return instalacionRepository.findByLocalIdOrderByNombreAsc(localId).stream()
            .map(i -> new InstalacionDto(i.getId(), i.getNombre(), i.getTipo(), i.getDescripcion(), i.getEstado().name()))
            .toList();
    }

    private void validarLocal(Long localId) {
        if (!localRepository.existsById(localId)) {
            throw new RecursoNoEncontradoException("Local no encontrado: " + localId);
        }
    }
}
