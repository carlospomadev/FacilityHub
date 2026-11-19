package com.facilityhub.service;

import com.facilityhub.api.dto.proveedor.ActualizarProveedorRequest;
import com.facilityhub.api.dto.proveedor.CambiarActivoRequest;
import com.facilityhub.api.dto.proveedor.CrearProveedorRequest;
import com.facilityhub.api.dto.proveedor.CrearTecnicoRequest;
import com.facilityhub.api.dto.proveedor.ProveedorDto;
import com.facilityhub.api.dto.proveedor.TecnicoDto;
import com.facilityhub.domain.AsignacionMantenimiento;
import com.facilityhub.domain.MotivoFinAsignacion;
import com.facilityhub.domain.Proveedor;
import com.facilityhub.domain.Rol;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.AsignacionMantenimientoRepository;
import com.facilityhub.repository.ProveedorRepository;
import com.facilityhub.repository.RolRepository;
import com.facilityhub.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// FH-006 / P6-B (D-1): alta y baja de proveedores y tecnicos solo para ADMIN; consulta para ADMIN y EXPLOTACION.
@Service
public class ProveedorService {
    static final String ROL_PROVEEDOR = "PROVEEDOR";

    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final AsignacionMantenimientoRepository asignacionRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ProveedorService(ProveedorRepository proveedorRepository, UsuarioRepository usuarioRepository,
                            RolRepository rolRepository, AsignacionMantenimientoRepository asignacionRepository,
                            DocumentoAutorizacionService autorizacion, PasswordEncoder passwordEncoder, Clock clock) {
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.asignacionRepository = asignacionRepository;
        this.autorizacion = autorizacion;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProveedorDto> listar(String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return proveedorRepository.findAllByOrderByRazonSocialAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProveedorDto obtener(Long proveedorId, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return toDto(buscar(proveedorId));
    }

    @Transactional
    public ProveedorDto crear(CrearProveedorRequest request, String email) {
        autorizacion.exigirAdmin(autorizacion.usuarioActual(email));
        Proveedor proveedor = new Proveedor(vacioANulo(request.cif()), request.razonSocial().trim(),
            vacioANulo(request.emailContacto()), vacioANulo(request.telefono()));
        return toDto(proveedorRepository.save(proveedor));
    }

    // Dar de baja la empresa (activo=false) cierra sus asignaciones activas: sus tecnicos dejan de ver esos trabajos.
    @Transactional
    public ProveedorDto actualizar(Long proveedorId, ActualizarProveedorRequest request, String email) {
        Usuario admin = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(admin);
        Proveedor proveedor = buscar(proveedorId);
        proveedor.actualizarDatos(vacioANulo(request.razonSocial()), vacioANulo(request.emailContacto()),
            vacioANulo(request.telefono()));
        if (request.activo() != null && request.activo() != proveedor.isActivo()) {
            proveedor.cambiarActivo(request.activo());
            if (!request.activo()) {
                LocalDateTime ahora = LocalDateTime.now(clock);
                for (AsignacionMantenimiento a : asignacionRepository.findByProveedorIdAndActivaTrue(proveedorId)) {
                    a.finalizar(MotivoFinAsignacion.PROVEEDOR_BAJA, admin, ahora);
                }
            }
        }
        return toDto(proveedor);
    }

    @Transactional
    public TecnicoDto crearTecnico(Long proveedorId, CrearTecnicoRequest request, String email) {
        autorizacion.exigirAdmin(autorizacion.usuarioActual(email));
        Proveedor proveedor = buscar(proveedorId);
        if (!proveedor.isActivo()) {
            throw new ConflictoException("El proveedor esta dado de baja");
        }
        String emailTecnico = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailIgnoreCase(emailTecnico)) {
            throw new ConflictoException("Ya existe un usuario con ese email");
        }
        Rol rol = rolRepository.findByCodigo(ROL_PROVEEDOR)
            .orElseThrow(() -> new ConflictoException("Rol PROVEEDOR no disponible"));
        Usuario tecnico = Usuario.nuevoTecnico(request.nombre().trim(), vacioANulo(request.apellidos()), emailTecnico,
            passwordEncoder.encode(request.password()), rol, proveedor, LocalDateTime.now(clock));
        return toTecnicoDto(usuarioRepository.save(tecnico));
    }

    @Transactional
    public TecnicoDto cambiarActivoTecnico(Long proveedorId, Long usuarioId, CambiarActivoRequest request, String email) {
        autorizacion.exigirAdmin(autorizacion.usuarioActual(email));
        Usuario tecnico = usuarioRepository.findById(usuarioId)
            .filter(u -> u.getProveedor() != null && u.getProveedor().getId().equals(proveedorId))
            .orElseThrow(() -> new RecursoNoEncontradoException("Tecnico no encontrado: " + usuarioId));
        tecnico.cambiarActivo(request.activo(), LocalDateTime.now(clock));
        return toTecnicoDto(tecnico);
    }

    private Proveedor buscar(Long proveedorId) {
        return proveedorRepository.findById(proveedorId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado: " + proveedorId));
    }

    private ProveedorDto toDto(Proveedor p) {
        List<TecnicoDto> tecnicos = p.getId() == null ? List.of()
            : usuarioRepository.findByProveedorIdOrderByNombreAsc(p.getId()).stream().map(this::toTecnicoDto).toList();
        return new ProveedorDto(p.getId(), p.getCif(), p.getRazonSocial(), p.getEmailContacto(), p.getTelefono(),
            p.isActivo(), tecnicos);
    }

    private TecnicoDto toTecnicoDto(Usuario u) {
        return new TecnicoDto(u.getId(), u.getNombre(), u.getApellidos(), u.getEmail(), u.isActivo());
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
