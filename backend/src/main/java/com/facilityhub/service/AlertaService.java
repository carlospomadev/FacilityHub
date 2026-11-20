package com.facilityhub.service;

import com.facilityhub.api.dto.alerta.AlertaDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.AlertaRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// FH-006 / P6-D1: alertas internas por eventos. Se emiten dentro de la transaccion del evento (MANDATORY: si el evento
// se revierte, la alerta tambien) con insercion idempotente por (destinatario, tipo, evento). Solo usuarios activos;
// el autor del evento no se avisa a si mismo. Mensajes sin datos personales. Sin tareas programadas.
@Service
public class AlertaService {
    private final AlertaRepository alertaRepository;
    private final UsuarioRepository usuarioRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final Clock clock;

    public AlertaService(AlertaRepository alertaRepository, UsuarioRepository usuarioRepository,
                         OcupacionLocalRepository ocupacionRepository, DocumentoAutorizacionService autorizacion, Clock clock) {
        this.alertaRepository = alertaRepository;
        this.usuarioRepository = usuarioRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.autorizacion = autorizacion;
        this.clock = clock;
    }

    // ---------- Emision (en la transaccion del evento) ----------

    // 1. Asignacion: tecnicos activos de la empresa asignada.
    @Transactional(propagation = Propagation.MANDATORY)
    public int asignacion(AsignacionMantenimiento a, Usuario actor) {
        Mantenimiento m = a.getMantenimiento();
        return emitir(TipoAlerta.ASIGNACION, "asignacion:" + a.getId(), usuarioRepository.tecnicosActivos(a.getProveedor().getId()),
            actor, m.getId(), m.getObligacion().getId(), null, "Nuevo trabajo asignado: " + descriptor(m));
    }

    // 2. Proveedor declarado por el inquilino: EXPLOTACION.
    @Transactional(propagation = Propagation.MANDATORY)
    public int proveedorDeclarado(ObligacionProveedor d, Usuario actor) {
        ObligacionMantenimiento o = d.getObligacion();
        return emitir(TipoAlerta.PROVEEDOR_DECLARADO, "obligacion_proveedor:" + d.getId(), usuarioRepository.activosConRol("EXPLOTACION"),
            actor, null, o.getId(), null, "Proveedor declarado pendiente de revision: " + o.getTipoMantenimiento().getNombre()
                + " · " + local(o));
    }

    // 3. Intervencion presentada para validar: EXPLOTACION (un aviso por envio).
    @Transactional(propagation = Propagation.MANDATORY)
    public int evidenciasPresentadas(Intervencion i, Usuario actor) {
        Mantenimiento m = i.getMantenimiento();
        return emitir(TipoAlerta.EVIDENCIAS_PRESENTADAS, "intervencion:" + i.getId() + ":envio:" + i.getNumeroEnvio(),
            usuarioRepository.activosConRol("EXPLOTACION"), actor, m.getId(), m.getObligacion().getId(), i.getId(),
            "Intervencion pendiente de validacion (envio " + i.getNumeroEnvio() + "): " + descriptor(m));
    }

    // 4. Correccion solicitada: tecnicos activos de la empresa de la intervencion.
    @Transactional(propagation = Propagation.MANDATORY)
    public int correccionSolicitada(Intervencion i, Usuario actor) {
        Mantenimiento m = i.getMantenimiento();
        return emitir(TipoAlerta.CORRECCION_SOLICITADA, "intervencion:" + i.getId() + ":envio:" + i.getNumeroEnvio() + ":correccion",
            usuarioRepository.tecnicosActivos(i.getProveedor().getId()), actor, m.getId(), m.getObligacion().getId(), i.getId(),
            "Correccion solicitada: " + descriptor(m));
    }

    // 5. Aprobacion: tecnicos de la empresa e inquilino de la ocupacion correspondiente (si la hay).
    @Transactional(propagation = Propagation.MANDATORY)
    public int aprobado(Intervencion i, Usuario actor) {
        Mantenimiento m = i.getMantenimiento();
        Set<Usuario> destinatarios = new LinkedHashSet<>(usuarioRepository.tecnicosActivos(i.getProveedor().getId()));
        destinatarios.addAll(inquilinosDelMantenimiento(m));
        return emitir(TipoAlerta.APROBADO, "intervencion:" + i.getId() + ":envio:" + i.getNumeroEnvio() + ":aprobada",
            List.copyOf(destinatarios), actor, m.getId(), m.getObligacion().getId(), i.getId(),
            "Mantenimiento aprobado: " + descriptor(m));
    }

    // Inquilino que debe conocer la aprobacion: el de la ocupacion vinculada al mantenimiento (si sigue vigente) o, sin
    // vinculo, el de la ocupacion vigente que cubre el hecho (misma regla de historial que P6-C2). Zona comun: ninguno.
    List<Usuario> inquilinosDelMantenimiento(Mantenimiento m) {
        ObligacionMantenimiento o = m.getObligacion();
        if (o.getLocal() == null) {
            return List.of();
        }
        return ocupacionRepository.ocupacionesVigentesDelLocal(o.getLocal().getId(), autorizacion.hoy()).stream()
            .filter(oc -> DocumentoAutorizacionService.cubreMantenimiento(oc, m))
            .map(oc -> oc.getInquilino().getId())
            .distinct()
            .flatMap(inquilinoId -> usuarioRepository.usuariosActivosDeInquilino(inquilinoId).stream())
            .toList();
    }

    private int emitir(TipoAlerta tipo, String eventoRef, List<Usuario> destinatarios, Usuario actor,
                       Long mantenimientoId, Long obligacionId, Long intervencionId, String mensaje) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        String texto = mensaje.length() > 300 ? mensaje.substring(0, 300) : mensaje;
        int creadas = 0;
        for (Usuario u : destinatarios) {
            if (actor != null && Objects.equals(u.getId(), actor.getId())) {
                continue; // el autor del evento no se avisa a si mismo
            }
            creadas += alertaRepository.insertarSiNoExiste(u.getId(), tipo.name(), eventoRef, mantenimientoId, obligacionId,
                intervencionId, texto, ahora);
        }
        return creadas;
    }

    static String descriptor(Mantenimiento m) {
        return m.getObligacion().getTipoMantenimiento().getNombre() + " · " + local(m.getObligacion()) + " · " + m.getFechaProgramada();
    }

    private static String local(ObligacionMantenimiento o) {
        return o.getLocal() == null ? "Zona comun" : o.getLocal().getCodigo();
    }

    // ---------- Consulta: cada usuario solo sus propias alertas ----------

    @Transactional(readOnly = true)
    public List<AlertaDto> propias(String email, boolean soloNoLeidas) {
        Long id = autorizacion.usuarioActual(email).getId();
        List<Alerta> alertas = soloNoLeidas
            ? alertaRepository.findTop100ByDestinatarioIdAndLeidaAtIsNullOrderByCreatedAtDescIdDesc(id)
            : alertaRepository.findTop100ByDestinatarioIdOrderByCreatedAtDescIdDesc(id);
        return alertas.stream().map(AlertaService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long noLeidas(String email) {
        return alertaRepository.countByDestinatarioIdAndLeidaAtIsNull(autorizacion.usuarioActual(email).getId());
    }

    // Ajena o inexistente: 404 uniforme. Idempotente.
    @Transactional
    public AlertaDto marcarLeida(Long alertaId, String email) {
        Long id = autorizacion.usuarioActual(email).getId();
        Alerta alerta = alertaRepository.findByIdAndDestinatarioId(alertaId, id).orElseThrow(RecursoNoDisponibleException::new);
        alerta.marcarLeida(LocalDateTime.now(clock));
        return toDto(alerta);
    }

    static AlertaDto toDto(Alerta a) {
        return new AlertaDto(a.getId(), a.getTipo().name(), a.getMensaje(), a.getMantenimientoId(), a.getObligacionId(),
            a.getIntervencionId(), a.getCreatedAt(), a.getLeidaAt());
    }
}
