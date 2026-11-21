package com.facilityhub.service;

import com.facilityhub.api.dto.inquilino.CertificadoInquilinoDto;
import com.facilityhub.api.dto.inquilino.CumplimientoInquilinoDto;
import com.facilityhub.api.dto.inquilino.FechaPrevistaRequest;
import com.facilityhub.api.dto.inquilino.MantenimientoInquilinoDetalleDto;
import com.facilityhub.api.dto.inquilino.ObligacionInquilinoDto;
import com.facilityhub.api.dto.inquilino.ProveedorBasicoDto;
import com.facilityhub.api.dto.proveedor.AsignacionDto;
import com.facilityhub.domain.AsignacionMantenimiento;
import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoValidacion;
import com.facilityhub.domain.Intervencion;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.ObligacionProveedor;
import com.facilityhub.domain.OcupacionLocal;
import com.facilityhub.domain.ResponsableObligacion;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.AsignacionMantenimientoRepository;
import com.facilityhub.repository.DocumentoMantenimientoRepository;
import com.facilityhub.repository.IntervencionRepository;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.ObligacionMantenimientoRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.ProveedorRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

// FH-006 / P6-C2: funcionalidades del INQUILINO. Solo obligaciones de locales con ocupacion propia vigente; solo
// gestiona las de responsabilidad INQUILINO (NHOOD: lectura). Historial protegido por ocupacion (C8). Los permisos se
// aplican aqui; Android solo muestra accionesPermitidas.
@Service
public class InquilinoService {
    public static final String ACCION_DECLARAR_PROVEEDOR = "DECLARAR_PROVEEDOR";
    public static final String ACCION_SOLICITAR_PROVEEDOR = "SOLICITAR_PROVEEDOR";
    public static final String ACCION_FIJAR_FECHA_PREVISTA = "FIJAR_FECHA_PREVISTA";
    public static final String ACCION_ASIGNAR_PROVEEDOR = "ASIGNAR_PROVEEDOR";

    private final OcupacionLocalRepository ocupacionRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final AsignacionMantenimientoRepository asignacionRepository;
    private final IntervencionRepository intervencionRepository;
    private final DocumentoMantenimientoRepository documentoRepository;
    private final ProveedorRepository proveedorRepository;
    private final DeclaracionProveedorService declaraciones;
    private final AsignacionService asignaciones;
    private final DocumentoAutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public InquilinoService(OcupacionLocalRepository ocupacionRepository, ObligacionMantenimientoRepository obligacionRepository,
                            MantenimientoRepository mantenimientoRepository, AsignacionMantenimientoRepository asignacionRepository,
                            IntervencionRepository intervencionRepository, DocumentoMantenimientoRepository documentoRepository,
                            ProveedorRepository proveedorRepository, DeclaracionProveedorService declaraciones,
                            AsignacionService asignaciones, DocumentoAutorizacionService autorizacion,
                            AuditoriaService auditoria, Clock clock) {
        this.ocupacionRepository = ocupacionRepository;
        this.obligacionRepository = obligacionRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.asignacionRepository = asignacionRepository;
        this.intervencionRepository = intervencionRepository;
        this.documentoRepository = documentoRepository;
        this.proveedorRepository = proveedorRepository;
        this.declaraciones = declaraciones;
        this.asignaciones = asignaciones;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public List<ObligacionInquilinoDto> obligaciones(String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Optional<Long> inquilinoId = autorizacion.inquilinoConAcceso(usuario);
        if (inquilinoId.isEmpty()) {
            return List.of();
        }
        List<ObligacionInquilinoDto> resultado = new ArrayList<>();
        for (OcupacionLocal ocupacion : ocupacionRepository.ocupacionesVigentesDeInquilino(inquilinoId.get(), autorizacion.hoy())) {
            for (ObligacionMantenimiento o : obligacionRepository.findByLocalIdAndActivoTrueOrderByProximaFechaAsc(ocupacion.getLocal().getId())) {
                resultado.add(toDto(o, ocupacion));
            }
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<MantenimientoInquilinoDetalleDto> mantenimientos(Long obligacionId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        DeclaracionProveedorService.AccesoObligacion acceso = declaraciones.obligacionVisibleParaInquilino(usuario, obligacionId);
        Optional<ObligacionProveedor> aceptada = declaraciones.proveedorAceptadoVigente(acceso.obligacion())
            .filter(d -> d.aceptadaPara(acceso.ocupacion()));
        return mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(obligacionId).stream()
            .filter(m -> DocumentoAutorizacionService.cubreMantenimiento(acceso.ocupacion(), m))
            .map(m -> toDto(m, aceptada.isPresent()))
            .toList();
    }

    // Busqueda exacta por CIF de un proveedor activo (sin listar el catalogo). Requiere alguna ocupacion vigente.
    @Transactional(readOnly = true)
    public ProveedorBasicoDto buscarProveedor(String cif, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Long inquilinoId = autorizacion.inquilinoConAcceso(usuario).orElseThrow(RecursoNoDisponibleException::new);
        if (ocupacionRepository.ocupacionesVigentesDeInquilino(inquilinoId, autorizacion.hoy()).isEmpty()
            || cif == null || cif.isBlank()) {
            throw new RecursoNoDisponibleException();
        }
        return proveedorRepository.findFirstByCifIgnoreCaseAndActivoTrue(cif.trim())
            .map(p -> new ProveedorBasicoDto(p.getId(), p.getRazonSocial()))
            .orElseThrow(RecursoNoDisponibleException::new);
    }

    // ---------- Escrituras (solo obligaciones INQUILINO) ----------

    @Transactional
    public MantenimientoInquilinoDetalleDto fijarFechaPrevista(Long mantenimientoId, FechaPrevistaRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Gestionable g = mantenimientoGestionable(usuario, mantenimientoId);
        if (request.version() == null) {
            throw new IllegalArgumentException("La version es obligatoria");
        }
        if (request.version() != g.mantenimiento().getVersion()) {
            throw new ConflictoException("El mantenimiento fue modificado por otro usuario; recarga los datos");
        }
        if (!g.mantenimiento().admiteAsignacion()) {
            throw new ConflictoException("El mantenimiento no admite cambios en estado " + g.mantenimiento().getEstado());
        }
        if (request.fechaPrevista() == null || request.fechaPrevista().isBefore(autorizacion.hoy())) {
            throw new IllegalArgumentException("La fecha prevista es obligatoria y no puede ser pasada");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        var anterior = g.mantenimiento().getFechaPrevistaInquilino();
        g.mantenimiento().vincularOcupacion(g.ocupacion(), ahora);
        g.mantenimiento().fijarFechaPrevista(request.fechaPrevista(), ahora);
        mantenimientoRepository.saveAndFlush(g.mantenimiento());
        auditoria.registrar("MANTENIMIENTO", mantenimientoId, "FIJAR_FECHA_PREVISTA", usuario, AuditoriaService.detalle(
            "anterior", anterior, "nueva", request.fechaPrevista(), "ocupacionId", g.ocupacion().getId()));
        return toDto(g.mantenimiento(), aceptadaPara(g).isPresent());
    }

    @Transactional
    public AsignacionDto asignarProveedorAceptado(Long mantenimientoId, Long tecnicoUsuarioId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Gestionable g = mantenimientoGestionable(usuario, mantenimientoId);
        ObligacionProveedor aceptada = aceptadaPara(g)
            .orElseThrow(() -> new ConflictoException("La obligacion no tiene un proveedor aceptado para tu ocupacion"));
        g.mantenimiento().vincularOcupacion(g.ocupacion(), LocalDateTime.now(clock));
        return asignaciones.asignarPorInquilino(g.mantenimiento(), aceptada.getProveedor(), tecnicoUsuarioId, usuario);
    }

    // ---------- Utilidades ----------

    private record Gestionable(Mantenimiento mantenimiento, OcupacionLocal ocupacion) {}

    // Mantenimiento visible para su ocupacion (si no, 404 uniforme) y de responsabilidad INQUILINO (si no, 403).
    private Gestionable mantenimientoGestionable(Usuario usuario, Long mantenimientoId) {
        Mantenimiento m = mantenimientoRepository.findById(mantenimientoId).orElseThrow(RecursoNoDisponibleException::new);
        DeclaracionProveedorService.AccesoObligacion acceso =
            declaraciones.obligacionVisibleParaInquilino(usuario, m.getObligacion().getId());
        if (!DocumentoAutorizacionService.cubreMantenimiento(acceso.ocupacion(), m)) {
            throw new RecursoNoDisponibleException();
        }
        if (acceso.obligacion().getResponsable() != ResponsableObligacion.INQUILINO) {
            throw new AccessDeniedException("Operacion no permitida");
        }
        return new Gestionable(m, acceso.ocupacion());
    }

    private Optional<ObligacionProveedor> aceptadaPara(Gestionable g) {
        return declaraciones.proveedorAceptadoVigente(g.mantenimiento().getObligacion())
            .filter(d -> d.aceptadaPara(g.ocupacion()));
    }

    private ObligacionInquilinoDto toDto(ObligacionMantenimiento o, OcupacionLocal ocupacion) {
        boolean propia = o.getResponsable() == ResponsableObligacion.INQUILINO;
        var declaracion = propia ? declaraciones.declaracionDeOcupacion(o.getId(), ocupacion)
            .map(DeclaracionProveedorService::toDto).orElse(null) : null;
        String local = o.getLocal().getNombre() == null ? o.getLocal().getCodigo()
            : o.getLocal().getCodigo() + " · " + o.getLocal().getNombre();
        return new ObligacionInquilinoDto(o.getId(), o.getLocal().getId(), local,
            o.getInstalacion() == null ? null : o.getInstalacion().getNombre(), o.getTipoMantenimiento().getNombre(),
            o.getTipoMantenimiento().getPeriodicidadMeses(), o.getProximaFecha(), o.getResponsable().name(), declaracion,
            propia ? List.of(ACCION_DECLARAR_PROVEEDOR, ACCION_SOLICITAR_PROVEEDOR) : List.of());
    }

    private MantenimientoInquilinoDetalleDto toDto(Mantenimiento m, boolean hayProveedorAceptado) {
        boolean propia = m.getObligacion().getResponsable() == ResponsableObligacion.INQUILINO;
        String proveedorAsignado = asignacionRepository.findByMantenimientoIdAndActivaTrue(m.getId())
            .map(AsignacionMantenimiento::getProveedor).map(p -> p.getRazonSocial()).orElse(null);
        // Solo el cumplimiento APROBADO y solo sus certificados VALIDO + COMPARTIDO (nunca evidencias internas ni
        // intentos NO_REALIZADO, que no se aprueban).
        CumplimientoInquilinoDto cumplimiento = intervencionRepository
            .findFirstByMantenimientoIdAndEstadoValidacionIn(m.getId(), EnumSet.of(EstadoValidacion.APROBADO))
            .map(this::cumplimiento).orElse(null);
        List<String> acciones = new ArrayList<>();
        if (propia && m.admiteAsignacion()) {
            acciones.add(ACCION_FIJAR_FECHA_PREVISTA);
            if (hayProveedorAceptado) {
                acciones.add(ACCION_ASIGNAR_PROVEEDOR);
            }
        }
        return new MantenimientoInquilinoDetalleDto(m.getId(), m.getObligacion().getId(), m.getFechaProgramada(),
            m.getFechaPrevistaInquilino(), m.getFechaRealizacion(), m.getEstado().name(), m.getDescripcion(),
            proveedorAsignado, cumplimiento, m.getVersion(), acciones);
    }

    private CumplimientoInquilinoDto cumplimiento(Intervencion i) {
        return cumplimiento(i, documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(i.getId()));
    }

    // FH-006 / P6-D3-2: cumplimiento visible para el inquilino (compartido con el historial del local). Solo
    // certificados que cumplen DocumentoAutorizacionService.visibleParaInquilino (VALIDO + COMPARTIDO).
    public static CumplimientoInquilinoDto cumplimiento(Intervencion i, List<DocumentoMantenimiento> documentosDeLaIntervencion) {
        List<CertificadoInquilinoDto> certificados = documentosDeLaIntervencion.stream()
            .filter(DocumentoAutorizacionService::visibleParaInquilino)
            .map(d -> new CertificadoInquilinoDto(d.getId(), d.getNombreOriginal(), d.getFechaSubida()))
            .toList();
        return new CumplimientoInquilinoDto(i.getId(), i.getFechaEjecucion(), i.getResultado().name(), certificados);
    }
}
