package com.facilityhub.service;

import com.facilityhub.api.dto.inquilino.AccionRevisionProveedor;
import com.facilityhub.api.dto.inquilino.DeclaracionPendienteDto;
import com.facilityhub.api.dto.inquilino.DeclaracionProveedorDto;
import com.facilityhub.api.dto.inquilino.RevisionDeclaracionRequest;
import com.facilityhub.domain.EstadoDeclaracionProveedor;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.ObligacionProveedor;
import com.facilityhub.domain.OcupacionLocal;
import com.facilityhub.domain.Proveedor;
import com.facilityhub.domain.ResponsableObligacion;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.ObligacionMantenimientoRepository;
import com.facilityhub.repository.ObligacionProveedorRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.ProveedorRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// FH-006 / P6-C2: proveedor declarado por el INQUILINO para una obligacion de su responsabilidad y su revision por
// gestion (ADMIN o EXPLOTACION). La declaracion pertenece a la ocupacion que la hace: un nuevo ocupante no la hereda.
@Service
public class DeclaracionProveedorService {
    static final String ENTIDAD = "OBLIGACION_PROVEEDOR";

    private final ObligacionProveedorRepository declaracionRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final ProveedorRepository proveedorRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final AlertaService alertas;
    private final Clock clock;

    public DeclaracionProveedorService(ObligacionProveedorRepository declaracionRepository,
                                       ObligacionMantenimientoRepository obligacionRepository,
                                       OcupacionLocalRepository ocupacionRepository, ProveedorRepository proveedorRepository,
                                       DocumentoAutorizacionService autorizacion, AuditoriaService auditoria,
                                       AlertaService alertas, Clock clock) {
        this.alertas = alertas;
        this.declaracionRepository = declaracionRepository;
        this.obligacionRepository = obligacionRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.proveedorRepository = proveedorRepository;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---------- INQUILINO ----------

    @Transactional
    public DeclaracionProveedorDto declarar(Long obligacionId, Long proveedorId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        AccesoObligacion acceso = obligacionGestionablePorInquilino(usuario, obligacionId);
        Proveedor proveedor = proveedorRepository.findById(proveedorId == null ? -1L : proveedorId)
            .filter(Proveedor::isActivo)
            .orElseThrow(RecursoNoDisponibleException::new);
        LocalDateTime ahora = LocalDateTime.now(clock);
        Optional<ObligacionProveedor> activa = declaracionRepository.findByObligacionIdAndActivoTrue(obligacionId);
        if (activa.isPresent()) {
            ObligacionProveedor actual = activa.get();
            if (actual.getOcupacion().getId().equals(acceso.ocupacion().getId())
                && actual.getProveedor().getId().equals(proveedor.getId())) {
                return toDto(actual); // idempotente: misma ocupacion y mismo proveedor, propuesto o aceptado
            }
            actual.finalizar(ahora);
            declaracionRepository.saveAndFlush(actual); // antes de insertar, por el indice unico de declaracion activa
        }
        // El indice unico de declaracion activa resuelve dos declaraciones simultaneas (409).
        ObligacionProveedor nueva = declaracionRepository.saveAndFlush(
            new ObligacionProveedor(acceso.obligacion(), proveedor, acceso.ocupacion(), usuario, ahora));
        auditoria.registrar(ENTIDAD, nueva.getId(), "DECLARAR_PROVEEDOR", usuario, AuditoriaService.detalle(
            "obligacionId", obligacionId, "proveedorId", proveedor.getId(), "ocupacionId", acceso.ocupacion().getId(),
            "sustituye", activa.map(ObligacionProveedor::getId).orElse(null)));
        alertas.proveedorDeclarado(nueva, usuario); // P6-D1: aviso a EXPLOTACION, en la misma transaccion
        return toDto(nueva);
    }

    // Declaracion visible para el inquilino: la ultima de SU ocupacion (activa o ya revisada). Nunca la de otro ocupante.
    @Transactional(readOnly = true)
    public Optional<ObligacionProveedor> declaracionDeOcupacion(Long obligacionId, OcupacionLocal ocupacion) {
        return declaracionRepository.findFirstByObligacionIdAndOcupacionIdOrderByIdDesc(obligacionId, ocupacion.getId());
    }

    // ---------- Gestion ----------

    @Transactional(readOnly = true)
    public List<DeclaracionPendienteDto> pendientes(String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return declaracionRepository.bandeja(EstadoDeclaracionProveedor.PROPUESTO).stream()
            .map(d -> new DeclaracionPendienteDto(d.getId(), d.getObligacion().getId(),
                d.getObligacion().getTipoMantenimiento().getNombre(), d.getObligacion().getLocal().getCodigo(),
                nombreInquilino(d.getOcupacion()), d.getProveedor().getId(), d.getProveedor().getRazonSocial(),
                d.getProveedor().getCif(), d.getDeclaradoAt(), d.getVersion()))
            .toList();
    }

    @Transactional
    public DeclaracionProveedorDto revisar(Long declaracionId, RevisionDeclaracionRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirGestion(usuario);
        ObligacionProveedor declaracion = declaracionRepository.bloquear(declaracionId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Declaracion no encontrada: " + declaracionId));
        if (request.version() == null) {
            throw new IllegalArgumentException("La version es obligatoria");
        }
        if (request.version() != declaracion.getVersion()) {
            throw new ConflictoException("La declaracion fue modificada por otro usuario; recarga los datos");
        }
        if (!declaracion.isActivo() || declaracion.getEstado() != EstadoDeclaracionProveedor.PROPUESTO) {
            throw new ConflictoException("La declaracion no esta pendiente de revision");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (request.accion() == AccionRevisionProveedor.ACEPTAR) {
            if (!vigente(declaracion.getOcupacion())) {
                throw new ConflictoException("La ocupacion que declaro el proveedor ya no esta vigente");
            }
            if (!declaracion.getProveedor().isActivo()) {
                throw new ConflictoException("El proveedor esta dado de baja");
            }
            declaracion.aceptar(usuario, request.motivo(), ahora);
        } else {
            declaracion.rechazar(usuario, request.motivo(), ahora);
        }
        declaracionRepository.saveAndFlush(declaracion);
        auditoria.registrar(ENTIDAD, declaracion.getId(),
            request.accion() == AccionRevisionProveedor.ACEPTAR ? "ACEPTAR_PROVEEDOR" : "RECHAZAR_PROVEEDOR", usuario,
            AuditoriaService.detalle("obligacionId", declaracion.getObligacion().getId(),
                "proveedorId", declaracion.getProveedor().getId()));
        return toDto(declaracion);
    }

    // Proveedor aceptado para la obligacion por la ocupacion vigente de su local (vacio en NHOOD o si no lo hay).
    @Transactional(readOnly = true)
    public Optional<ObligacionProveedor> proveedorAceptadoVigente(ObligacionMantenimiento obligacion) {
        if (obligacion.getResponsable() != ResponsableObligacion.INQUILINO || obligacion.getLocal() == null) {
            return Optional.empty();
        }
        List<OcupacionLocal> vigentes = ocupacionRepository.ocupacionesVigentesDelLocal(obligacion.getLocal().getId(),
            autorizacion.hoy());
        return declaracionRepository.findByObligacionIdAndActivoTrue(obligacion.getId())
            .filter(d -> vigentes.stream().anyMatch(d::aceptadaPara));
    }

    // ---------- Acceso comun del inquilino ----------

    public record AccesoObligacion(ObligacionMantenimiento obligacion, OcupacionLocal ocupacion) {}

    // Lectura: obligacion activa de un local con ocupacion vigente propia (si no, 404 uniforme).
    @Transactional(readOnly = true)
    public AccesoObligacion obligacionVisibleParaInquilino(Usuario usuario, Long obligacionId) {
        ObligacionMantenimiento obligacion = obligacionRepository.findById(obligacionId)
            .filter(ObligacionMantenimiento::isActivo)
            .filter(o -> o.getLocal() != null)
            .orElseThrow(RecursoNoDisponibleException::new);
        OcupacionLocal ocupacion = autorizacion.ocupacionVigente(usuario, obligacion.getLocal().getId())
            .orElseThrow(RecursoNoDisponibleException::new);
        return new AccesoObligacion(obligacion, ocupacion);
    }

    // Escritura: ademas, la obligacion debe ser de responsabilidad INQUILINO (las de NHOOD no se modifican: 403).
    @Transactional(readOnly = true)
    public AccesoObligacion obligacionGestionablePorInquilino(Usuario usuario, Long obligacionId) {
        AccesoObligacion acceso = obligacionVisibleParaInquilino(usuario, obligacionId);
        if (acceso.obligacion().getResponsable() != ResponsableObligacion.INQUILINO) {
            throw new AccessDeniedException("Operacion no permitida");
        }
        return acceso;
    }

    boolean vigente(OcupacionLocal ocupacion) {
        return ocupacion.isActivo() && !ocupacion.getFechaInicio().isAfter(autorizacion.hoy())
            && (ocupacion.getFechaFin() == null || !ocupacion.getFechaFin().isBefore(autorizacion.hoy()));
    }

    static String nombreInquilino(OcupacionLocal ocupacion) {
        var i = ocupacion.getInquilino();
        return i.getNombreComercial() != null ? i.getNombreComercial() : i.getRazonSocial();
    }

    static DeclaracionProveedorDto toDto(ObligacionProveedor d) {
        return new DeclaracionProveedorDto(d.getId(), d.getObligacion().getId(), d.getProveedor().getId(),
            d.getProveedor().getRazonSocial(), d.getEstado().name(), d.getMotivoRevision(), d.getDeclaradoAt(),
            d.getRevisadoAt(), d.isActivo(), d.getVersion());
    }
}
