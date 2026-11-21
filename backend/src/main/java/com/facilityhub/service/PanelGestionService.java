package com.facilityhub.service;

import com.facilityhub.api.dto.gestion.ResumenGestionDto;
import com.facilityhub.api.dto.gestion.TrabajoGestionDto;
import com.facilityhub.domain.AsignacionMantenimiento;
import com.facilityhub.domain.EstadoDeclaracionProveedor;
import com.facilityhub.domain.EstadoSolicitudProveedor;
import com.facilityhub.domain.EstadoValidacion;
import com.facilityhub.domain.Intervencion;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.repository.AsignacionMantenimientoRepository;
import com.facilityhub.repository.IntervencionRepository;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.ObligacionProveedorRepository;
import com.facilityhub.repository.SolicitudProveedorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// FH-006 / P6-D3-1: panel de gestion (ADMIN y EXPLOTACION, alcance global provisional D5). Solo lectura.
// Filtros (hoy = reloj Europe/Madrid; preaviso = facilityhub.recordatorios.preaviso-dias, el mismo de P6-D2):
//  - VENCIDOS / PROXIMOS: mantenimientos abiertos (MantenimientoRepository.abiertosHasta) clasificados con
//    ReglaVencimiento, la regla de los recordatorios. Un FINALIZADO con intervencion en PENDIENTE_VALIDACION no es un
//    vencido: va a PENDIENTE_VALIDACION. APROBADO y CANCELADO nunca son abiertos. Incluye zonas comunes.
//  - PENDIENTE_VALIDACION / REQUIERE_CORRECCION: IntervencionRepository.bandeja, la misma de la bandeja de validacion.
//  - SIN_ASIGNAR: VENCIDOS + PROXIMOS que admiten asignacion y no tienen asignacion activa.
// El resumen cuenta exactamente los mismos listados (calculados una vez), asi que siempre es coherente con ellos.
@Service
@Transactional(readOnly = true)
public class PanelGestionService {
    public enum Filtro { VENCIDOS, PROXIMOS, PENDIENTE_VALIDACION, REQUIERE_CORRECCION, SIN_ASIGNAR }

    private static final Set<EstadoValidacion> ABIERTAS =
        EnumSet.of(EstadoValidacion.BORRADOR, EstadoValidacion.PENDIENTE_VALIDACION, EstadoValidacion.REQUIERE_CORRECCION);

    private final MantenimientoRepository mantenimientoRepository;
    private final IntervencionRepository intervencionRepository;
    private final AsignacionMantenimientoRepository asignacionRepository;
    private final ObligacionProveedorRepository declaracionRepository;
    private final SolicitudProveedorRepository solicitudRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final int preavisoDias;

    public PanelGestionService(MantenimientoRepository mantenimientoRepository, IntervencionRepository intervencionRepository,
                               AsignacionMantenimientoRepository asignacionRepository,
                               ObligacionProveedorRepository declaracionRepository,
                               SolicitudProveedorRepository solicitudRepository, DocumentoAutorizacionService autorizacion,
                               @Value("${facilityhub.recordatorios.preaviso-dias:15}") int preavisoDias) {
        if (preavisoDias < 0) {
            throw new IllegalArgumentException("Configuracion de preaviso no valida");
        }
        this.mantenimientoRepository = mantenimientoRepository;
        this.intervencionRepository = intervencionRepository;
        this.asignacionRepository = asignacionRepository;
        this.declaracionRepository = declaracionRepository;
        this.solicitudRepository = solicitudRepository;
        this.autorizacion = autorizacion;
        this.preavisoDias = preavisoDias;
    }

    public ResumenGestionDto resumen(String email) {
        exigirGestion(email);
        LocalDate hoy = autorizacion.hoy();
        Map<Filtro, List<TrabajoGestionDto>> panel = panel(hoy);
        return new ResumenGestionDto(hoy, preavisoDias,
            panel.get(Filtro.VENCIDOS).size(), panel.get(Filtro.PROXIMOS).size(),
            panel.get(Filtro.PENDIENTE_VALIDACION).size(), panel.get(Filtro.REQUIERE_CORRECCION).size(),
            panel.get(Filtro.SIN_ASIGNAR).size(),
            declaracionRepository.bandeja(EstadoDeclaracionProveedor.PROPUESTO).size(),
            solicitudRepository.findByEstadoOrderByIdAsc(EstadoSolicitudProveedor.PENDIENTE).size());
    }

    public List<TrabajoGestionDto> trabajos(String filtro, String email) {
        exigirGestion(email);
        Filtro f = filtro(filtro);
        return panel(autorizacion.hoy()).get(f);
    }

    // Calculo unico de los cinco listados (sin N+1: intervenciones y asignaciones se cargan por lote).
    Map<Filtro, List<TrabajoGestionDto>> panel(LocalDate hoy) {
        Map<Filtro, List<TrabajoGestionDto>> panel = new EnumMap<>(Filtro.class);
        for (Filtro f : Filtro.values()) {
            panel.put(f, new ArrayList<>());
        }

        List<Mantenimiento> abiertos = mantenimientoRepository.abiertosHasta(hoy.plusDays(preavisoDias));
        List<Long> ids = abiertos.stream().map(Mantenimiento::getId).toList();
        Map<Long, Intervencion> abiertaPorMantenimiento = ids.isEmpty() ? Map.of()
            : intervencionRepository.findByMantenimientoIdInAndEstadoValidacionIn(ids, ABIERTAS).stream()
                .collect(Collectors.toMap(i -> i.getMantenimiento().getId(), Function.identity(), (a, b) -> a));
        Map<Long, AsignacionMantenimiento> asignaciones = asignacionesActivas(ids);

        for (Mantenimiento m : abiertos) {
            Intervencion abierta = abiertaPorMantenimiento.get(m.getId());
            boolean enValidacion = abierta != null && abierta.getEstadoValidacion() == EstadoValidacion.PENDIENTE_VALIDACION;
            ReglaVencimiento.Situacion situacion =
                ReglaVencimiento.situacion(m.getFechaProgramada(), hoy, preavisoDias, enValidacion);
            Filtro f = switch (situacion) {
                case VENCIDO -> Filtro.VENCIDOS;
                case PROXIMO -> Filtro.PROXIMOS;
                default -> null; // en validacion: se lista desde la bandeja
            };
            if (f == null) {
                continue;
            }
            AsignacionMantenimiento asignacion = asignaciones.get(m.getId());
            TrabajoGestionDto fila = fila(m, abierta, asignacion, hoy);
            panel.get(f).add(fila);
            if (asignacion == null && m.admiteAsignacion()) {
                panel.get(Filtro.SIN_ASIGNAR).add(fila);
            }
        }

        bandeja(EstadoValidacion.PENDIENTE_VALIDACION, hoy, panel.get(Filtro.PENDIENTE_VALIDACION));
        bandeja(EstadoValidacion.REQUIERE_CORRECCION, hoy, panel.get(Filtro.REQUIERE_CORRECCION));
        panel.replaceAll((f, lista) -> List.copyOf(lista));
        return panel;
    }

    private void bandeja(EstadoValidacion estado, LocalDate hoy, List<TrabajoGestionDto> destino) {
        List<Intervencion> intervenciones = intervencionRepository.bandeja(estado);
        Map<Long, AsignacionMantenimiento> asignaciones =
            asignacionesActivas(intervenciones.stream().map(i -> i.getMantenimiento().getId()).toList());
        for (Intervencion i : intervenciones) {
            destino.add(fila(i.getMantenimiento(), i, asignaciones.get(i.getMantenimiento().getId()), hoy));
        }
    }

    private Map<Long, AsignacionMantenimiento> asignacionesActivas(Collection<Long> mantenimientoIds) {
        if (mantenimientoIds.isEmpty()) {
            return Map.of();
        }
        return asignacionRepository.activasDeMantenimientos(mantenimientoIds).stream()
            .collect(Collectors.toMap(a -> a.getMantenimiento().getId(), Function.identity(), (a, b) -> a));
    }

    private static TrabajoGestionDto fila(Mantenimiento m, Intervencion intervencion, AsignacionMantenimiento asignacion,
                                          LocalDate hoy) {
        ObligacionMantenimiento o = m.getObligacion();
        String proveedor = asignacion != null ? asignacion.getProveedor().getRazonSocial()
            : intervencion != null && intervencion.getProveedor() != null ? intervencion.getProveedor().getRazonSocial()
            : null;
        return new TrabajoGestionDto(m.getId(), intervencion == null ? null : intervencion.getId(),
            o.getTipoMantenimiento().getNombre(), o.getCentro().getNombre(),
            o.getLocal() == null ? null : o.getLocal().getCodigo(), o.getLocal() == null, o.getResponsable().name(),
            m.getFechaProgramada(), m.getFechaRealizacion(), m.getEstado().name(),
            intervencion == null ? null : intervencion.getEstadoValidacion().name(), proveedor,
            ChronoUnit.DAYS.between(hoy, m.getFechaProgramada()));
    }

    private void exigirGestion(String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
    }

    private static Filtro filtro(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El filtro es obligatorio");
        }
        try {
            return Filtro.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Filtro no valido");
        }
    }
}
