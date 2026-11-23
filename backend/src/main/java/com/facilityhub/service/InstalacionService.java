package com.facilityhub.service;

import com.facilityhub.api.dto.CrearInstalacionRequest;
import com.facilityhub.api.dto.InstalacionCentroDto;
import com.facilityhub.domain.Centro;
import com.facilityhub.domain.EstadoInstalacion;
import com.facilityhub.domain.Instalacion;
import com.facilityhub.domain.LocalComercial;
import com.facilityhub.repository.CentroRepository;
import com.facilityhub.repository.InstalacionRepository;
import com.facilityhub.repository.LocalComercialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FH-010A / RF-07: registro y consulta de instalaciones de un centro, solo para gestion (ADMIN y EXPLOTACION).
 * Reglas:
 *  - el centro debe existir (404) y estar activo (409);
 *  - el local, si se informa, debe existir (404), pertenecer al centro (400) y estar activo (409); sin local = zona comun;
 *  - no se admiten dos instalaciones con el mismo nombre (sin distinguir mayusculas) en la misma ubicacion (409);
 *  - el alta no crea obligaciones ni mantenimientos (RF-09 es otra fase).
 */
@Service
public class InstalacionService {
    private final CentroRepository centroRepository;
    private final LocalComercialRepository localRepository;
    private final InstalacionRepository instalacionRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final Clock clock;

    public InstalacionService(CentroRepository centroRepository, LocalComercialRepository localRepository,
                              InstalacionRepository instalacionRepository, DocumentoAutorizacionService autorizacion,
                              Clock clock) {
        this.centroRepository = centroRepository;
        this.localRepository = localRepository;
        this.instalacionRepository = instalacionRepository;
        this.autorizacion = autorizacion;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InstalacionCentroDto> listarDelCentro(Long centroId, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        if (!centroRepository.existsById(centroId)) {
            throw new RecursoNoEncontradoException("Centro no encontrado: " + centroId);
        }
        return instalacionRepository.findDelCentro(centroId).stream().map(InstalacionService::toDto).toList();
    }

    @Transactional
    public InstalacionCentroDto crear(Long centroId, CrearInstalacionRequest request, String email) {
        // Permiso antes de leer nada: PROVEEDOR e INQUILINO reciben 403 sin conocer si el centro existe.
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        // Bloquea la fila del centro hasta el commit: dos altas simultaneas del mismo centro se serializan.
        Centro centro = centroRepository.findParaAlta(centroId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Centro no encontrado: " + centroId));
        if (!centro.isActivo()) {
            throw new ConflictoException("El centro esta inactivo");
        }
        LocalComercial local = null;
        if (request.localId() != null) {
            local = localRepository.findById(request.localId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Local no encontrado: " + request.localId()));
            if (!local.getCentro().getId().equals(centro.getId())) {
                throw new IllegalArgumentException("El local no pertenece al centro indicado");
            }
            if (!local.isActivo()) {
                throw new ConflictoException("El local esta inactivo");
            }
        }
        String nombre = request.nombre().trim();
        String tipo = request.tipo().trim();
        boolean duplicada = local != null
            ? instalacionRepository.existsByLocalIdAndNombreIgnoreCase(local.getId(), nombre)
            : instalacionRepository.existsByCentroIdAndLocalIsNullAndNombreIgnoreCase(centro.getId(), nombre);
        if (duplicada) {
            throw new ConflictoException("Ya existe una instalacion con ese nombre en esta ubicacion");
        }
        EstadoInstalacion estado = request.estado() == null || request.estado().isBlank()
            ? EstadoInstalacion.ACTIVA
            : EstadoInstalacion.valueOf(request.estado());
        Instalacion instalacion = new Instalacion(centro, local, nombre, tipo, vacioANulo(request.descripcion()), estado,
            LocalDateTime.now(clock));
        // saveAndFlush: cualquier rechazo de la BD (FK, CHECK) aflora aqui y revierte la transaccion completa.
        return toDto(instalacionRepository.saveAndFlush(instalacion));
    }

    private static InstalacionCentroDto toDto(Instalacion i) {
        LocalComercial l = i.getLocal();
        return new InstalacionCentroDto(i.getId(), i.getNombre(), i.getTipo(), i.getDescripcion(), i.getEstado().name(),
            i.getCentro().getId(), l == null ? null : l.getId(), l == null ? null : l.getCodigo(),
            l == null ? null : l.getNombre());
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
