package com.facilityhub.service;

import com.facilityhub.api.dto.alerta.ResultadoRecordatoriosDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.AlertaRepository;
import com.facilityhub.repository.AsignacionMantenimientoRepository;
import com.facilityhub.repository.IntervencionRepository;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

// FH-006 / P6-D2: recordatorios de vencimiento sobre las alertas de V8. Solo se ejecuta bajo control (tarea programada
// desactivada por defecto o llamada manual de ADMIN); nunca al arrancar. Idempotente: cada aviso lleva un evento_ref
// determinista por periodo y la insercion ignora duplicados. Un bloqueo consultivo evita ejecuciones simultaneas.
//
// Reglas (hoy = reloj Europe/Madrid). Candidato: obligacion ACTIVA, mantenimiento no CANCELADO (FINALIZADO solo con
// intervencion en PENDIENTE_VALIDACION: "enviado" no es "cumplido"), sin intervencion APROBADO y como maximo
// `antiguedad-maxima-dias` vencido.
//  - VENCIMIENTO_PROXIMO: hoy <= fecha <= hoy + preaviso, salvo intervencion ya presentada (PENDIENTE_VALIDACION).
//    Un aviso por mantenimiento y fecha programada.
//  - MANTENIMIENTO_VENCIDO: fecha < hoy sin intervencion en PENDIENTE_VALIDACION (sin intervencion, BORRADOR,
//    REQUIERE_CORRECCION o intentos NO_REALIZADO cerrados). Un aviso por bloque de `cadencia-vencido-dias`.
//  - VALIDACION_PENDIENTE_VENCIDA: fecha < hoy con intervencion en PENDIENTE_VALIDACION. Un aviso por bloque.
// Destinatarios (solo usuarios activos): EXPLOTACION siempre; tecnicos del proveedor con asignacion ACTIVA (salvo en
// validacion pendiente, que depende de Explotacion); si el responsable es INQUILINO, los usuarios del inquilino de la
// ocupacion que cubre el mantenimiento. ADMIN no recibe recordatorios.
@Service
public class RecordatorioService {
    static final long CLAVE_BLOQUEO = 6_002_001L;

    private final MantenimientoRepository mantenimientoRepository;
    private final IntervencionRepository intervencionRepository;
    private final AsignacionMantenimientoRepository asignacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlertaRepository alertaRepository;
    private final AlertaService alertaService;
    private final DocumentoAutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final int preavisoDias;
    private final int cadenciaVencidoDias;
    private final int antiguedadMaximaDias;

    public RecordatorioService(MantenimientoRepository mantenimientoRepository, IntervencionRepository intervencionRepository,
                               AsignacionMantenimientoRepository asignacionRepository, UsuarioRepository usuarioRepository,
                               AlertaRepository alertaRepository, AlertaService alertaService,
                               DocumentoAutorizacionService autorizacion, AuditoriaService auditoria, Clock clock,
                               @Value("${facilityhub.recordatorios.preaviso-dias:15}") int preavisoDias,
                               @Value("${facilityhub.recordatorios.cadencia-vencido-dias:7}") int cadenciaVencidoDias,
                               @Value("${facilityhub.recordatorios.antiguedad-maxima-dias:90}") int antiguedadMaximaDias) {
        if (preavisoDias < 0 || cadenciaVencidoDias < 1 || antiguedadMaximaDias < 1) {
            throw new IllegalArgumentException("Configuracion de recordatorios no valida");
        }
        this.mantenimientoRepository = mantenimientoRepository;
        this.intervencionRepository = intervencionRepository;
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.alertaRepository = alertaRepository;
        this.alertaService = alertaService;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.clock = clock;
        this.preavisoDias = preavisoDias;
        this.cadenciaVencidoDias = cadenciaVencidoDias;
        this.antiguedadMaximaDias = antiguedadMaximaDias;
    }

    // Ejecucion manual (ADMIN). La ejecucion real queda auditada; la simulacion no escribe nada.
    @Transactional
    public ResultadoRecordatoriosDto ejecutarManual(boolean simulacion, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        ResultadoRecordatoriosDto r = ejecutar(simulacion);
        if (!simulacion) {
            auditoria.registrar("RECORDATORIOS", 0L, "EJECUTAR", usuario, AuditoriaService.detalle(
                "fecha", r.fecha(), "candidatos", r.candidatos(), "avisosCreados", r.avisos()));
        }
        return r;
    }

    // simulacion = true: calcula y cuenta los avisos que se crearian, sin escribir nada.
    @Transactional
    public ResultadoRecordatoriosDto ejecutar(boolean simulacion) {
        if (!alertaRepository.bloquearEjecucion(CLAVE_BLOQUEO)) {
            throw new ConflictoException("Ya hay una ejecucion de recordatorios en curso");
        }
        LocalDate hoy = LocalDate.now(clock);
        List<Mantenimiento> candidatos = mantenimientoRepository.candidatosRecordatorio(
            hoy.minusDays(antiguedadMaximaDias), hoy.plusDays(preavisoDias));
        List<Usuario> explotacion = usuarioRepository.activosConRol("EXPLOTACION");
        int proximos = 0, vencidos = 0, pendientes = 0, avisos = 0;
        for (Mantenimiento m : candidatos) {
            Optional<Intervencion> enValidacion = intervencionRepository.findFirstByMantenimientoIdAndEstadoValidacionIn(
                m.getId(), List.of(EstadoValidacion.PENDIENTE_VALIDACION));
            Recordatorio r = clasificar(m, enValidacion.orElse(null), hoy);
            if (r == null) {
                continue;
            }
            switch (r.tipo()) {
                case VENCIMIENTO_PROXIMO -> proximos++;
                case MANTENIMIENTO_VENCIDO -> vencidos++;
                default -> pendientes++;
            }
            List<Usuario> destinatarios = destinatarios(m, r.tipo(), explotacion);
            if (simulacion) {
                avisos += (int) destinatarios.stream()
                    .filter(u -> !alertaRepository.existsByDestinatarioIdAndTipoAndEventoRef(u.getId(), r.tipo(), r.eventoRef()))
                    .count();
            } else {
                avisos += alertaService.recordatorio(r.tipo(), r.eventoRef(), destinatarios, m, r.intervencionId(), r.mensaje());
            }
        }
        return new ResultadoRecordatoriosDto(hoy, simulacion, candidatos.size(), proximos, vencidos, pendientes, avisos);
    }

    record Recordatorio(TipoAlerta tipo, String eventoRef, Long intervencionId, String mensaje) {
    }

    // Regla de clasificacion (pura, sin acceso a datos).
    Recordatorio clasificar(Mantenimiento m, Intervencion enValidacion, LocalDate hoy) {
        LocalDate fecha = m.getFechaProgramada();
        String descriptor = AlertaService.descriptor(m);
        ReglaVencimiento.Situacion situacion = ReglaVencimiento.situacion(fecha, hoy, preavisoDias, enValidacion != null);
        if (situacion == ReglaVencimiento.Situacion.FUERA) {
            return null;
        }
        if (situacion == ReglaVencimiento.Situacion.PROXIMO) {
            return new Recordatorio(TipoAlerta.VENCIMIENTO_PROXIMO, "vence:" + m.getId() + ":" + fecha + ":p" + preavisoDias,
                null, "Mantenimiento proximo a vencer (" + ChronoUnit.DAYS.between(hoy, fecha) + " dias): " + descriptor);
        }
        long diasVencido = ChronoUnit.DAYS.between(fecha, hoy);
        if (diasVencido > antiguedadMaximaDias) {
            return null;
        }
        long bloque = (diasVencido - 1) / cadenciaVencidoDias;
        if (situacion == ReglaVencimiento.Situacion.VALIDACION_PENDIENTE_VENCIDA) {
            return new Recordatorio(TipoAlerta.VALIDACION_PENDIENTE_VENCIDA,
                "valpend:" + m.getId() + ":" + enValidacion.getId() + ":b" + bloque, enValidacion.getId(),
                "Validacion pendiente de un mantenimiento vencido: " + descriptor);
        }
        return new Recordatorio(TipoAlerta.MANTENIMIENTO_VENCIDO, "vencido:" + m.getId() + ":" + fecha + ":b" + bloque, null,
            "Mantenimiento vencido sin cumplimiento aprobado (" + diasVencido + " dias): " + descriptor);
    }

    List<Usuario> destinatarios(Mantenimiento m, TipoAlerta tipo, List<Usuario> explotacion) {
        Set<Usuario> d = new LinkedHashSet<>(explotacion);
        if (tipo != TipoAlerta.VALIDACION_PENDIENTE_VENCIDA) {
            asignacionRepository.findByMantenimientoIdAndActivaTrue(m.getId())
                .ifPresent(a -> d.addAll(usuarioRepository.tecnicosActivos(a.getProveedor().getId())));
        }
        if (m.getObligacion().getResponsable() == ResponsableObligacion.INQUILINO) {
            d.addAll(alertaService.inquilinosDelMantenimiento(m));
        }
        return List.copyOf(d);
    }
}
