package com.facilityhub.service;

import com.facilityhub.api.dto.inquilino.AccionSolicitudProveedor;
import com.facilityhub.api.dto.inquilino.ResolverSolicitudRequest;
import com.facilityhub.api.dto.inquilino.SolicitudProveedorDto;
import com.facilityhub.api.dto.inquilino.SolicitudProveedorRequest;
import com.facilityhub.domain.EstadoSolicitudProveedor;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.Proveedor;
import com.facilityhub.domain.SolicitudProveedor;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.ProveedorRepository;
import com.facilityhub.repository.SolicitudProveedorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// FH-006 / P6-C2 (D-1): el INQUILINO solicita incorporar un proveedor nuevo; no crea empresas ni usuarios tecnicos.
// ADMIN la atiende vinculando la empresa que da de alta por /api/proveedores, o la rechaza con respuesta.
// EXPLOTACION solo consulta.
@Service
public class SolicitudProveedorService {
    static final String ENTIDAD = "SOLICITUD_PROVEEDOR";

    private final SolicitudProveedorRepository solicitudRepository;
    private final ProveedorRepository proveedorRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final DeclaracionProveedorService declaraciones;
    private final DocumentoAutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public SolicitudProveedorService(SolicitudProveedorRepository solicitudRepository, ProveedorRepository proveedorRepository,
                                     OcupacionLocalRepository ocupacionRepository, DeclaracionProveedorService declaraciones,
                                     DocumentoAutorizacionService autorizacion, AuditoriaService auditoria, Clock clock) {
        this.solicitudRepository = solicitudRepository;
        this.proveedorRepository = proveedorRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.declaraciones = declaraciones;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---------- INQUILINO ----------

    @Transactional
    public SolicitudProveedorDto crear(SolicitudProveedorRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Inquilino inquilino = inquilinoConOcupacionVigente(usuario);
        ObligacionMantenimiento obligacion = request.obligacionId() == null ? null
            : declaraciones.obligacionGestionablePorInquilino(usuario, request.obligacionId()).obligacion();
        // El indice unico de solicitud pendiente por empresa evita el doble envio (409).
        SolicitudProveedor solicitud = solicitudRepository.saveAndFlush(new SolicitudProveedor(inquilino, obligacion, usuario,
            request.razonSocial(), request.cif(), request.contacto(), request.observaciones(), LocalDateTime.now(clock)));
        auditoria.registrar(ENTIDAD, solicitud.getId(), "SOLICITAR_PROVEEDOR", usuario, AuditoriaService.detalle(
            "inquilinoId", inquilino.getId(), "obligacionId", obligacion == null ? null : obligacion.getId()));
        return toDto(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudProveedorDto> propias(String email) {
        Inquilino inquilino = inquilinoConOcupacionVigente(autorizacion.usuarioActual(email));
        return solicitudRepository.findByInquilinoIdOrderByIdDesc(inquilino.getId()).stream().map(this::toDto).toList();
    }

    // ---------- Gestion ----------

    @Transactional(readOnly = true)
    public List<SolicitudProveedorDto> listar(EstadoSolicitudProveedor estado, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        List<SolicitudProveedor> lista = estado == null ? solicitudRepository.findAllByOrderByIdDesc()
            : solicitudRepository.findByEstadoOrderByIdAsc(estado);
        return lista.stream().map(this::toDto).toList();
    }

    @Transactional
    public SolicitudProveedorDto resolver(Long solicitudId, ResolverSolicitudRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        SolicitudProveedor solicitud = solicitudRepository.bloquear(solicitudId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada: " + solicitudId));
        if (request.version() == null) {
            throw new IllegalArgumentException("La version es obligatoria");
        }
        if (request.version() != solicitud.getVersion()) {
            throw new ConflictoException("La solicitud fue modificada por otro usuario; recarga los datos");
        }
        if (solicitud.getEstado() != EstadoSolicitudProveedor.PENDIENTE) {
            throw new ConflictoException("La solicitud ya esta resuelta");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (request.accion() == AccionSolicitudProveedor.ATENDER) {
            if (request.proveedorId() == null) {
                throw new IllegalArgumentException("Indica el proveedor dado de alta");
            }
            Proveedor proveedor = proveedorRepository.findById(request.proveedorId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado: " + request.proveedorId()));
            if (!proveedor.isActivo()) {
                throw new ConflictoException("El proveedor esta dado de baja");
            }
            solicitud.atender(proveedor, usuario, request.respuesta(), ahora);
        } else {
            solicitud.rechazar(usuario, request.respuesta(), ahora);
        }
        solicitudRepository.saveAndFlush(solicitud);
        auditoria.registrar(ENTIDAD, solicitud.getId(),
            request.accion() == AccionSolicitudProveedor.ATENDER ? "ATENDER_SOLICITUD" : "RECHAZAR_SOLICITUD", usuario,
            AuditoriaService.detalle("proveedorId", solicitud.getProveedor() == null ? null : solicitud.getProveedor().getId()));
        return toDto(solicitud);
    }

    // El inquilino debe estar activo y tener al menos una ocupacion vigente (si no, 404 uniforme).
    private Inquilino inquilinoConOcupacionVigente(Usuario usuario) {
        Long inquilinoId = autorizacion.inquilinoConAcceso(usuario).orElseThrow(RecursoNoDisponibleException::new);
        if (ocupacionRepository.ocupacionesVigentesDeInquilino(inquilinoId, autorizacion.hoy()).isEmpty()) {
            throw new RecursoNoDisponibleException();
        }
        return usuario.getInquilino();
    }

    private SolicitudProveedorDto toDto(SolicitudProveedor s) {
        Inquilino i = s.getInquilino();
        return new SolicitudProveedorDto(s.getId(), i.getNombreComercial() != null ? i.getNombreComercial() : i.getRazonSocial(),
            s.getObligacion() == null ? null : s.getObligacion().getId(), s.getRazonSocial(), s.getCif(), s.getContacto(),
            s.getObservaciones(), s.getEstado().name(), s.getProveedor() == null ? null : s.getProveedor().getId(),
            s.getProveedor() == null ? null : s.getProveedor().getRazonSocial(), s.getRespuesta(), s.getCreatedAt(),
            s.getAtendidaAt(), s.getVersion());
    }
}
