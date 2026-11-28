package com.facilityhub.service;

import com.facilityhub.api.dto.CrearObligacionRequest;
import com.facilityhub.api.dto.ObligacionCentroDto;
import com.facilityhub.api.dto.TipoMantenimientoDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FH-010B / RF-09: alta de obligaciones de mantenimiento, su consulta por centro y el catalogo de tipos (solo lectura).
 * Solo gestion (ADMIN y EXPLOTACION). Decisiones aprobadas:
 *  - D1: responsable INQUILINO exige local con ocupacion vigente (409 si no la hay; 400 en zona comun), como
 *        {@link ObligacionService#cambiarResponsable};
 *  - D2: el alta crea SOLO la obligacion (ni mantenimientos, ni asignaciones, ni alertas);
 *  - D3: duplicados activos rechazados en el servicio (bloqueo del centro) y en BD (uq_obligacion_activa y V10);
 *  - D4: GET de tipos activos.
 */
@Service
public class ObligacionAltaService {
    private final CentroRepository centroRepository;
    private final LocalComercialRepository localRepository;
    private final InstalacionRepository instalacionRepository;
    private final TipoMantenimientoRepository tipoRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final Clock clock;

    public ObligacionAltaService(CentroRepository centroRepository, LocalComercialRepository localRepository,
                                 InstalacionRepository instalacionRepository, TipoMantenimientoRepository tipoRepository,
                                 ObligacionMantenimientoRepository obligacionRepository,
                                 OcupacionLocalRepository ocupacionRepository, DocumentoAutorizacionService autorizacion,
                                 Clock clock) {
        this.centroRepository = centroRepository;
        this.localRepository = localRepository;
        this.instalacionRepository = instalacionRepository;
        this.tipoRepository = tipoRepository;
        this.obligacionRepository = obligacionRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.autorizacion = autorizacion;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TipoMantenimientoDto> listarTiposActivos(String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return tipoRepository.findByActivoTrueOrderByNombreAsc().stream()
            .map(t -> new TipoMantenimientoDto(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion(),
                t.getPeriodicidadMeses(), t.isRequiereDocumento(), t.getReglaRecalculo().name()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ObligacionCentroDto> listarDelCentro(Long centroId, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        if (!centroRepository.existsById(centroId)) {
            throw new RecursoNoEncontradoException("Centro no encontrado: " + centroId);
        }
        return obligacionRepository.findActivasDelCentro(centroId).stream().map(ObligacionAltaService::toDto).toList();
    }

    @Transactional
    public ObligacionCentroDto crear(Long centroId, CrearObligacionRequest request, String email) {
        // Permiso antes de leer nada: PROVEEDOR e INQUILINO reciben 403 sin conocer si el centro existe.
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        // Bloquea la fila del centro hasta el commit: las altas simultaneas del mismo centro se serializan y la
        // comprobacion de duplicados no tiene carreras (el indice unico es la segunda barrera).
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
        }

        Instalacion instalacion = null;
        if (request.instalacionId() != null) {
            instalacion = instalacionRepository.findById(request.instalacionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Instalacion no encontrada: " + request.instalacionId()));
            if (!instalacion.getCentro().getId().equals(centro.getId())) {
                throw new IllegalArgumentException("La instalacion no pertenece al centro indicado");
            }
            LocalComercial localInstalacion = instalacion.getLocal();
            if (localInstalacion != null) {
                if (local == null) {
                    // Instalacion de un local sin localId: la obligacion es de ese local (coherente con el trigger).
                    local = localInstalacion;
                } else if (!local.getId().equals(localInstalacion.getId())) {
                    throw new IllegalArgumentException("La instalacion no pertenece al local indicado");
                }
            } else if (local != null) {
                throw new IllegalArgumentException("La instalacion es de zona comun: la obligacion no puede tener local");
            }
        }
        // FH-014 / RF-04 (D7): bloqueo de la fila del local (despues del centro) frente a una desactivacion simultanea.
        if (local != null && !localRepository.bloquearYEstaActivo(local.getId())) {
            throw new ConflictoException("El local esta inactivo");
        }

        TipoMantenimiento tipo = tipoRepository.findById(request.tipoMantenimientoId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de mantenimiento no encontrado: " + request.tipoMantenimientoId()));
        if (!tipo.isActivo()) {
            throw new ConflictoException("El tipo de mantenimiento esta inactivo");
        }
        if (tipo.getPeriodicidadMeses() == null || tipo.getPeriodicidadMeses() <= 0) {
            throw new ConflictoException("El tipo de mantenimiento no tiene una periodicidad valida");
        }

        // D1: mismas reglas que cambiarResponsable.
        ResponsableObligacion responsable = ResponsableObligacion.valueOf(request.responsable());
        if (responsable == ResponsableObligacion.INQUILINO) {
            if (local == null) {
                throw new IllegalArgumentException("Una obligacion de zona comun no puede ser responsabilidad del inquilino");
            }
            if (!ocupacionRepository.existeOcupacionVigenteDelLocal(local.getId(), autorizacion.hoy())) {
                throw new ConflictoException("El local no tiene una ocupacion vigente");
            }
        }

        // Fechas sin zona horaria. plusMonths ajusta al ultimo dia del mes (31/01 + 1 mes = 28 o 29/02).
        LocalDate inicio = request.fechaInicio();
        LocalDate proxima = request.proximaFecha() != null ? request.proximaFecha() : inicio.plusMonths(tipo.getPeriodicidadMeses());
        if (proxima.isBefore(inicio)) {
            throw new IllegalArgumentException("La proxima fecha no puede ser anterior a la fecha de inicio");
        }

        Long instalacionClave = instalacion == null ? 0L : instalacion.getId();
        boolean duplicada = local != null
            ? obligacionRepository.existeActivaEnLocal(local.getId(), instalacionClave, tipo.getId())
            : obligacionRepository.existeActivaEnZonaComun(centro.getId(), instalacionClave, tipo.getId());
        if (duplicada) {
            throw new ConflictoException("Ya existe una obligacion activa de ese tipo en esta ubicacion");
        }

        // D2: solo la obligacion. saveAndFlush: cualquier rechazo de la BD aflora aqui y revierte la transaccion.
        ObligacionMantenimiento obligacion = new ObligacionMantenimiento(centro, local, instalacion, tipo, responsable,
            inicio, proxima, vacioANulo(request.observaciones()), LocalDateTime.now(clock));
        return toDto(obligacionRepository.saveAndFlush(obligacion));
    }

    private static ObligacionCentroDto toDto(ObligacionMantenimiento o) {
        LocalComercial l = o.getLocal();
        Instalacion i = o.getInstalacion();
        TipoMantenimiento t = o.getTipoMantenimiento();
        return new ObligacionCentroDto(o.getId(), o.getCentro().getId(),
            l == null ? null : l.getId(), l == null ? null : l.getCodigo(), l == null ? null : l.getNombre(),
            i == null ? null : i.getId(), i == null ? null : i.getNombre(),
            t.getId(), t.getNombre(), t.getPeriodicidadMeses(),
            o.getResponsable().name(), o.getFechaInicio(), o.getProximaFecha(), o.getEstado().name(), o.isActivo(),
            o.getObservaciones());
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
