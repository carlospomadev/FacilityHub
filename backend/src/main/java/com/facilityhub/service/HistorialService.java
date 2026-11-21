package com.facilityhub.service;

import com.facilityhub.api.dto.historial.HistorialGestionDto;
import com.facilityhub.api.dto.historial.HistorialInquilinoDto;
import com.facilityhub.api.dto.intervencion.DocumentoIntervencionDto;
import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoValidacion;
import com.facilityhub.domain.Intervencion;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.OcupacionLocal;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.DocumentoMantenimientoRepository;
import com.facilityhub.repository.IntervencionRepository;
import com.facilityhub.repository.LocalComercialRepository;
import com.facilityhub.repository.MantenimientoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// FH-006 / P6-D3-2: historial consolidado de un local (GET /api/locales/{id}/historial). Solo lectura.
//  - Gestion (ADMIN, EXPLOTACION; alcance global D5): intervenciones APROBADO y CERRADO_SIN_CUMPLIMIENTO con todos sus
//    documentos, y los mantenimientos historicos P5 (validacion_requerida = FALSE) como "Historico P5 - sin validacion",
//    con su estado y fechas reales y sin presentarlos nunca como aprobados.
//  - INQUILINO: solo intervenciones APROBADO de mantenimientos cubiertos por su ocupacion vigente del local
//    (puedeAccederLocal + cubreMantenimiento, regla C8/D-8) y solo certificados VALIDO + COMPARTIDO. Sin P5, sin
//    pendientes, sin cierres sin cumplimiento, sin observaciones internas ni documentos internos.
//  - Local inexistente, ajeno o sin ocupacion vigente: 404 uniforme. PROVEEDOR: 403 (URL y servicio).
// Consultas por lote (mantenimientos del local, intervenciones y documentos): sin N+1.
@Service
@Transactional(readOnly = true)
public class HistorialService {
    public static final String APROBADO = "APROBADO";
    public static final String CERRADO_SIN_CUMPLIMIENTO = "CERRADO_SIN_CUMPLIMIENTO";
    public static final String HISTORICO_P5 = "HISTORICO_P5";
    static final String ETIQUETA_APROBADO = "Cumplimiento aprobado";
    static final String ETIQUETA_CERRADO = "Cerrado sin cumplimiento";
    static final String ETIQUETA_P5 = "Histórico P5 — sin validación";

    private final LocalComercialRepository localRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final IntervencionRepository intervencionRepository;
    private final DocumentoMantenimientoRepository documentoRepository;
    private final DocumentoAutorizacionService autorizacion;

    public HistorialService(LocalComercialRepository localRepository, MantenimientoRepository mantenimientoRepository,
                            IntervencionRepository intervencionRepository,
                            DocumentoMantenimientoRepository documentoRepository, DocumentoAutorizacionService autorizacion) {
        this.localRepository = localRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.intervencionRepository = intervencionRepository;
        this.documentoRepository = documentoRepository;
        this.autorizacion = autorizacion;
    }

    // Devuelve List<HistorialGestionDto> (gestion) o List<HistorialInquilinoDto> (INQUILINO).
    public List<?> historial(Long localId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (autorizacion.esProveedor(usuario)) {
            throw new AccessDeniedException("Operacion no permitida");
        }
        if (localId == null || !localRepository.existsById(localId) || !autorizacion.puedeAccederLocal(usuario, localId)) {
            throw new RecursoNoDisponibleException();
        }
        if (autorizacion.esGestion(usuario)) {
            return gestion(localId);
        }
        OcupacionLocal ocupacion = autorizacion.ocupacionVigente(usuario, localId)
            .orElseThrow(RecursoNoDisponibleException::new);
        return inquilino(localId, ocupacion);
    }

    List<HistorialGestionDto> gestion(Long localId) {
        List<Mantenimiento> mantenimientos = mantenimientoRepository.delLocal(localId);
        if (mantenimientos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = mantenimientos.stream().map(Mantenimiento::getId).toList();
        Map<Long, List<Intervencion>> intervenciones = intervencionRepository.historialDeMantenimientos(ids,
                EnumSet.of(EstadoValidacion.APROBADO, EstadoValidacion.CERRADO_SIN_CUMPLIMIENTO)).stream()
            .collect(Collectors.groupingBy(i -> i.getMantenimiento().getId()));
        List<DocumentoMantenimiento> documentos = documentoRepository.findByMantenimientoIdInOrderByFechaSubidaAscIdAsc(ids);
        Map<Long, List<DocumentoMantenimiento>> porIntervencion = documentos.stream()
            .filter(d -> d.getIntervencion() != null)
            .collect(Collectors.groupingBy(d -> d.getIntervencion().getId()));
        Map<Long, List<DocumentoMantenimiento>> sinIntervencion = documentos.stream()
            .filter(d -> d.getIntervencion() == null)
            .collect(Collectors.groupingBy(d -> d.getMantenimiento().getId()));

        List<Entrada<HistorialGestionDto>> entradas = new ArrayList<>();
        for (Mantenimiento m : mantenimientos) {
            for (Intervencion i : intervenciones.getOrDefault(m.getId(), List.of())) {
                boolean aprobado = i.getEstadoValidacion() == EstadoValidacion.APROBADO;
                entradas.add(new Entrada<>(i.getFechaEjecucion() == null ? m.fechaDelHecho() : i.getFechaEjecucion(),
                    gestionDto(aprobado ? APROBADO : CERRADO_SIN_CUMPLIMIENTO, aprobado ? ETIQUETA_APROBADO : ETIQUETA_CERRADO,
                        m, i, porIntervencion.getOrDefault(i.getId(), List.of()))));
            }
            if (!m.isValidacionRequerida()) {
                entradas.add(new Entrada<>(m.fechaDelHecho(), gestionDto(HISTORICO_P5, ETIQUETA_P5, m, null,
                    sinIntervencion.getOrDefault(m.getId(), List.of()))));
            }
        }
        return ordenar(entradas);
    }

    List<HistorialInquilinoDto> inquilino(Long localId, OcupacionLocal ocupacion) {
        // Solo mantenimientos P6 cubiertos por la ocupacion vigente; los historicos P5 nunca tienen cumplimiento aprobado.
        Map<Long, Mantenimiento> cubiertos = mantenimientoRepository.delLocal(localId).stream()
            .filter(Mantenimiento::isValidacionRequerida)
            .filter(m -> DocumentoAutorizacionService.cubreMantenimiento(ocupacion, m))
            .collect(Collectors.toMap(Mantenimiento::getId, m -> m));
        if (cubiertos.isEmpty()) {
            return List.of();
        }
        List<Intervencion> aprobadas = intervencionRepository.historialDeMantenimientos(cubiertos.keySet(),
            EnumSet.of(EstadoValidacion.APROBADO));
        if (aprobadas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DocumentoMantenimiento>> porIntervencion = documentoRepository
            .findByIntervencionIdInOrderByFechaSubidaAscIdAsc(aprobadas.stream().map(Intervencion::getId).toList()).stream()
            .collect(Collectors.groupingBy(d -> d.getIntervencion().getId()));
        List<Entrada<HistorialInquilinoDto>> entradas = new ArrayList<>();
        for (Intervencion i : aprobadas) {
            Mantenimiento m = cubiertos.get(i.getMantenimiento().getId());
            ObligacionMantenimiento o = m.getObligacion();
            entradas.add(new Entrada<>(i.getFechaEjecucion(), new HistorialInquilinoDto(m.getId(), o.getId(),
                o.getTipoMantenimiento().getNombre(), o.getInstalacion() == null ? null : o.getInstalacion().getNombre(),
                m.getFechaProgramada(), m.getFechaRealizacion(),
                InquilinoService.cumplimiento(i, porIntervencion.getOrDefault(i.getId(), List.of())))));
        }
        return ordenar(entradas);
    }

    private static HistorialGestionDto gestionDto(String tipo, String etiqueta, Mantenimiento m, Intervencion i,
                                                  List<DocumentoMantenimiento> documentos) {
        ObligacionMantenimiento o = m.getObligacion();
        return new HistorialGestionDto(tipo, etiqueta, m.getId(), o.getId(), o.getTipoMantenimiento().getNombre(),
            o.getInstalacion() == null ? null : o.getInstalacion().getNombre(), o.getResponsable().name(),
            m.getFechaProgramada(), m.getFechaRealizacion(), m.getEstado().name(), m.getObservaciones(),
            i == null ? null : i.getId(), i == null ? null : i.getFechaEjecucion(),
            i == null || i.getResultado() == null ? null : i.getResultado().name(),
            i == null ? null : i.getEstadoValidacion().name(),
            i == null ? null : i.getProveedor().getRazonSocial(),
            documentos.stream().map(d -> new DocumentoIntervencionDto(d.getId(), d.getNombreOriginal(),
                d.getCategoria() == null ? null : d.getCategoria().name(), d.getEstadoRevision().name(),
                d.getVisibilidad().name(), d.getFechaSubida())).toList());
    }

    // Mas reciente primero (fecha del hecho; a igualdad, orden de llegada).
    private static <T> List<T> ordenar(List<Entrada<T>> entradas) {
        return entradas.stream()
            .sorted(Comparator.comparing((Entrada<T> e) -> e.fecha(), Comparator.nullsLast(Comparator.reverseOrder())))
            .map(Entrada::dto)
            .toList();
    }

    private record Entrada<T>(LocalDate fecha, T dto) {}
}
