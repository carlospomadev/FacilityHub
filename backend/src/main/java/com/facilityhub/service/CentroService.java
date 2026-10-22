package com.facilityhub.service;

import com.facilityhub.api.dto.CentroDto;
import com.facilityhub.api.dto.LocalDto;
import com.facilityhub.domain.Centro;
import com.facilityhub.repository.CentroRepository;
import com.facilityhub.repository.LocalComercialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CentroService {
    private final CentroRepository centroRepository;
    private final LocalComercialRepository localRepository;

    public CentroService(CentroRepository centroRepository, LocalComercialRepository localRepository) {
        this.centroRepository = centroRepository;
        this.localRepository = localRepository;
    }

    public List<CentroDto> listarCentrosActivos() {
        return centroRepository.findByActivoTrueOrderByNombreAsc().stream()
            .map(this::toDto)
            .toList();
    }

    public CentroDto obtenerCentro(Long id) {
        return centroRepository.findById(id)
            .map(this::toDto)
            .orElseThrow(() -> new RecursoNoEncontradoException("Centro no encontrado: " + id));
    }

    public List<LocalDto> listarLocales(Long centroId) {
        if (!centroRepository.existsById(centroId)) {
            throw new RecursoNoEncontradoException("Centro no encontrado: " + centroId);
        }
        return localRepository.findByCentroIdAndActivoTrueOrderByCodigoAsc(centroId).stream()
            .map(local -> new LocalDto(local.getId(), local.getCodigo(), local.getNombre(), local.getPlanta(), local.getUbicacion(), local.isActivo()))
            .toList();
    }

    private CentroDto toDto(Centro centro) {
        return new CentroDto(centro.getId(), centro.getCodigo(), centro.getNombre(), centro.getCiudad(), centro.isActivo());
    }
}
