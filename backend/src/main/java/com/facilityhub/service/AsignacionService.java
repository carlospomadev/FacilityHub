package com.facilityhub.service;

import com.facilityhub.api.dto.proveedor.AsignacionDto;
import com.facilityhub.api.dto.proveedor.AsignacionRequest;
import com.facilityhub.domain.AsignacionMantenimiento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.MotivoFinAsignacion;
import com.facilityhub.domain.Proveedor;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.AsignacionMantenimientoRepository;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.ProveedorRepository;
import com.facilityhub.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

// FH-006 / P6-B: asignacion de mantenimientos a proveedores. ADMIN en cualquier obligacion; EXPLOTACION en las de
// responsabilidad NHOOD. Una asignacion activa por mantenimiento (indice unico parcial); reasignar cierra la anterior.
@Service
public class AsignacionService {
    private final MantenimientoRepository mantenimientoRepository;
    private final AsignacionMantenimientoRepository asignacionRepository;
    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final PoliticaAccesoService politica;
    private final DeclaracionProveedorService declaraciones;
    private final AuditoriaService auditoria;
    private final AlertaService alertas;
    private final Clock clock;

    public AsignacionService(MantenimientoRepository mantenimientoRepository,
                             AsignacionMantenimientoRepository asignacionRepository,
                             ProveedorRepository proveedorRepository, UsuarioRepository usuarioRepository,
                             DocumentoAutorizacionService autorizacion, PoliticaAccesoService politica,
                             DeclaracionProveedorService declaraciones, AuditoriaService auditoria, AlertaService alertas,
                             Clock clock) {
        this.alertas = alertas;
        this.mantenimientoRepository = mantenimientoRepository;
        this.asignacionRepository = asignacionRepository;
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.autorizacion = autorizacion;
        this.politica = politica;
        this.declaraciones = declaraciones;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AsignacionDto consultar(Long mantenimientoId, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        buscarMantenimiento(mantenimientoId);
        return asignacionRepository.findByMantenimientoIdAndActivaTrue(mantenimientoId)
            .map(this::toDto)
            .orElseThrow(() -> new RecursoNoEncontradoException("Sin asignacion activa: " + mantenimientoId));
    }

    @Transactional
    public AsignacionDto asignar(Long mantenimientoId, AsignacionRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirGestion(usuario);
        Mantenimiento mantenimiento = buscarMantenimiento(mantenimientoId);
        politica.exigirAsignar(usuario, mantenimiento.getObligacion());
        if (!mantenimiento.admiteAsignacion()) {
            throw new ConflictoException("El mantenimiento no admite asignacion en estado " + mantenimiento.getEstado());
        }
        Proveedor proveedor = proveedorRepository.findById(request.proveedorId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado: " + request.proveedorId()));
        if (!proveedor.isActivo()) {
            throw new ConflictoException("El proveedor esta dado de baja");
        }
        // FH-006 / P6-C2: en obligaciones INQUILINO solo el proveedor ACEPTADO de la ocupacion vigente.
        if (mantenimiento.getObligacion().getResponsable() == com.facilityhub.domain.ResponsableObligacion.INQUILINO
            && declaraciones.proveedorAceptadoVigente(mantenimiento.getObligacion())
                .filter(d -> d.getProveedor().getId().equals(proveedor.getId())).isEmpty()) {
            throw new ConflictoException("En obligaciones del inquilino solo se asigna su proveedor aceptado");
        }
        Usuario tecnico = tecnicoValido(request.tecnicoUsuarioId(), proveedor);
        return toDto(asignarProveedor(mantenimiento, proveedor, tecnico, usuario));
    }

    // FH-006 / P6-C2: asignacion hecha por el INQUILINO con su proveedor aceptado (las comprobaciones de acceso y de
    // declaracion las hace InquilinoService).
    @Transactional
    public AsignacionDto asignarPorInquilino(Mantenimiento mantenimiento, Proveedor proveedor, Long tecnicoUsuarioId,
                                             Usuario usuario) {
        if (!mantenimiento.admiteAsignacion()) {
            throw new ConflictoException("El mantenimiento no admite asignacion en estado " + mantenimiento.getEstado());
        }
        if (!proveedor.isActivo()) {
            throw new ConflictoException("El proveedor esta dado de baja");
        }
        return toDto(asignarProveedor(mantenimiento, proveedor, tecnicoValido(tecnicoUsuarioId, proveedor), usuario));
    }

    // Nucleo comun: idempotente si no cambia nada; reasignar cierra la anterior. Auditado.
    private AsignacionMantenimiento asignarProveedor(Mantenimiento mantenimiento, Proveedor proveedor, Usuario tecnico,
                                                     Usuario usuario) {
        Long mantenimientoId = mantenimiento.getId();
        Optional<AsignacionMantenimiento> actual = asignacionRepository.findByMantenimientoIdAndActivaTrue(mantenimientoId);
        if (actual.isPresent()) {
            AsignacionMantenimiento anterior = actual.get();
            boolean mismoTecnico = Objects.equals(idDe(anterior.getTecnico()), idDe(tecnico));
            if (anterior.getProveedor().getId().equals(proveedor.getId()) && mismoTecnico) {
                return anterior; // idempotente
            }
            anterior.finalizar(MotivoFinAsignacion.REASIGNADA, usuario, LocalDateTime.now(clock));
            // Se vuelca antes de insertar la nueva para respetar el indice unico de asignacion activa.
            asignacionRepository.saveAndFlush(anterior);
        }
        AsignacionMantenimiento nueva = asignacionRepository.saveAndFlush(new AsignacionMantenimiento(mantenimiento,
            proveedor, tecnico, usuario, LocalDateTime.now(clock)));
        auditoria.registrar("MANTENIMIENTO", mantenimientoId, "ASIGNAR_PROVEEDOR", usuario, AuditoriaService.detalle(
            "asignacionId", nueva.getId(), "proveedorId", proveedor.getId(),
            "sustituye", actual.map(AsignacionMantenimiento::getId).orElse(null)));
        alertas.asignacion(nueva, usuario); // P6-D1: aviso a los tecnicos de la empresa, en la misma transaccion
        return nueva;
    }

    @Transactional
    public void retirar(Long mantenimientoId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirGestion(usuario);
        Mantenimiento mantenimiento = buscarMantenimiento(mantenimientoId);
        politica.exigirAsignar(usuario, mantenimiento.getObligacion());
        AsignacionMantenimiento activa = asignacionRepository.findByMantenimientoIdAndActivaTrue(mantenimientoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Sin asignacion activa: " + mantenimientoId));
        activa.finalizar(MotivoFinAsignacion.RETIRADA, usuario, LocalDateTime.now(clock));
    }

    private Mantenimiento buscarMantenimiento(Long mantenimientoId) {
        return mantenimientoRepository.findById(mantenimientoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Mantenimiento no encontrado: " + mantenimientoId));
    }

    // El tecnico, si se indica, debe ser un usuario activo con rol PROVEEDOR de la misma empresa (la BD lo exige
    // tambien con una clave foranea compuesta).
    private Usuario tecnicoValido(Long tecnicoId, Proveedor proveedor) {
        if (tecnicoId == null) {
            return null;
        }
        return usuarioRepository.findById(tecnicoId)
            .filter(Usuario::isActivo)
            .filter(autorizacion::esProveedor)
            .filter(u -> u.getProveedor() != null && u.getProveedor().getId().equals(proveedor.getId()))
            .orElseThrow(() -> new IllegalArgumentException("El tecnico no pertenece al proveedor o no esta activo"));
    }

    private static Long idDe(Usuario usuario) {
        return usuario == null ? null : usuario.getId();
    }

    private AsignacionDto toDto(AsignacionMantenimiento a) {
        Usuario t = a.getTecnico();
        return new AsignacionDto(a.getId(), a.getMantenimiento().getId(), a.getProveedor().getId(),
            a.getProveedor().getRazonSocial(), t == null ? null : t.getId(), nombreCompleto(t), a.getAsignadoAt(),
            a.isActiva());
    }

    static String nombreCompleto(Usuario u) {
        if (u == null) {
            return null;
        }
        return u.getApellidos() == null ? u.getNombre() : u.getNombre() + " " + u.getApellidos();
    }
}
