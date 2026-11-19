package com.facilityhub.service;

import com.facilityhub.api.dto.CentroDto;
import com.facilityhub.api.dto.LocalDto;
import com.facilityhub.domain.Centro;
import com.facilityhub.domain.LocalComercial;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.CentroRepository;
import com.facilityhub.repository.LocalComercialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// FH-005A2-B: el usuario y su rol se reconsultan en BD. Gestion (ADMIN/EXPLOTACION) conserva el comportamiento
// previo (alcance global provisional); INQUILINO solo ve centros y locales con ocupacion propia vigente,
// filtrados en consulta. Ajeno o inexistente para INQUILINO: 404 uniforme.
@Service
@Transactional(readOnly = true)
public class CentroService {
    private final CentroRepository centroRepository;
    private final LocalComercialRepository localRepository;
    private final DocumentoAutorizacionService autorizacion;

    public CentroService(CentroRepository centroRepository, LocalComercialRepository localRepository,
                         DocumentoAutorizacionService autorizacion) {
        this.centroRepository = centroRepository;
        this.localRepository = localRepository;
        this.autorizacion = autorizacion;
    }

    public List<CentroDto> listarCentros(String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (autorizacion.esGestion(usuario)) {
            return centroRepository.findByActivoTrueOrderByNombreAsc().stream().map(this::toDto).toList();
        }
        // Rol no reconocido o inquilino sin asociacion activa: lista vacia, sin consultar centros.
        Optional<Long> inquilinoId = autorizacion.inquilinoConAcceso(usuario);
        if (inquilinoId.isEmpty()) {
            return List.of();
        }
        return centroRepository.findVisiblesParaInquilino(inquilinoId.get(), autorizacion.hoy()).stream()
            .map(this::toDto)
            .toList();
    }

    public CentroDto obtenerCentro(Long id, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (autorizacion.esGestion(usuario)) {
            // Semantica previa para gestion (incluye centros inactivos y el mensaje existente).
            return centroRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RecursoNoEncontradoException("Centro no encontrado: " + id));
        }
        exigirCentroVisible(usuario, id);
        return centroRepository.findById(id).map(this::toDto).orElseThrow(RecursoNoDisponibleException::new);
    }

    public List<LocalDto> listarLocales(Long centroId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (autorizacion.esGestion(usuario)) {
            if (!centroRepository.existsById(centroId)) {
                throw new RecursoNoEncontradoException("Centro no encontrado: " + centroId);
            }
            return localRepository.findByCentroIdAndActivoTrueOrderByCodigoAsc(centroId).stream()
                .map(this::toDto)
                .toList();
        }
        Long inquilinoId = exigirCentroVisible(usuario, centroId);
        return localRepository.findVisiblesEnCentroParaInquilino(centroId, inquilinoId, autorizacion.hoy()).stream()
            .map(this::toDto)
            .toList();
    }

    // Centro activo con al menos un local activo ocupado hoy por el inquilino; si no, el mismo 404 sin IDs.
    private Long exigirCentroVisible(Usuario usuario, Long centroId) {
        Long inquilinoId = autorizacion.inquilinoConAcceso(usuario).orElseThrow(RecursoNoDisponibleException::new);
        if (!centroRepository.esVisibleParaInquilino(centroId, inquilinoId, autorizacion.hoy())) {
            throw new RecursoNoDisponibleException();
        }
        return inquilinoId;
    }

    private CentroDto toDto(Centro centro) {
        return new CentroDto(centro.getId(), centro.getCodigo(), centro.getNombre(), centro.getCiudad(), centro.isActivo());
    }

    private LocalDto toDto(LocalComercial local) {
        return new LocalDto(local.getId(), local.getCodigo(), local.getNombre(), local.getPlanta(), local.getUbicacion(), local.isActivo());
    }
}
