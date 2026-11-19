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
    private final Clock clock;

    public AsignacionService(MantenimientoRepository mantenimientoRepository,
                             AsignacionMantenimientoRepository asignacionRepository,
                             ProveedorRepository proveedorRepository, UsuarioRepository usuarioRepository,
                             DocumentoAutorizacionService autorizacion, PoliticaAccesoService politica, Clock clock) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.asignacionRepository = asignacionRepository;
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.autorizacion = autorizacion;
        this.politica = politica;
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
        Usuario tecnico = tecnicoValido(request.tecnicoUsuarioId(), proveedor);

        Optional<AsignacionMantenimiento> actual = asignacionRepository.findByMantenimientoIdAndActivaTrue(mantenimientoId);
        if (actual.isPresent()) {
            AsignacionMantenimiento anterior = actual.get();
            boolean mismoTecnico = Objects.equals(idDe(anterior.getTecnico()), idDe(tecnico));
            if (anterior.getProveedor().getId().equals(proveedor.getId()) && mismoTecnico) {
                return toDto(anterior); // idempotente
            }
            anterior.finalizar(MotivoFinAsignacion.REASIGNADA, usuario, LocalDateTime.now(clock));
            // Se vuelca antes de insertar la nueva para respetar el indice unico de asignacion activa.
            asignacionRepository.saveAndFlush(anterior);
        }
        AsignacionMantenimiento nueva = new AsignacionMantenimiento(mantenimiento, proveedor, tecnico, usuario,
            LocalDateTime.now(clock));
        return toDto(asignacionRepository.saveAndFlush(nueva));
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
