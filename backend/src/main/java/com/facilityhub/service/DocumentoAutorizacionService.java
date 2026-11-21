package com.facilityhub.service;

import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoRevisionDocumento;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.OcupacionLocal;
import com.facilityhub.domain.Usuario;
import com.facilityhub.domain.VisibilidadDocumento;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

// Decisiones de acceso documental por recurso. El usuario y su rol se reconsultan en BD:
// el claim de rol del JWT no basta para autorizar.
@Service
@Transactional(readOnly = true)
public class DocumentoAutorizacionService {
    static final String ROL_ADMIN = "ADMIN";
    static final String ROL_EXPLOTACION = "EXPLOTACION";
    static final String ROL_INQUILINO = "INQUILINO";
    static final String ROL_PROVEEDOR = "PROVEEDOR";

    private final UsuarioRepository usuarioRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final Clock clock;

    public DocumentoAutorizacionService(UsuarioRepository usuarioRepository,
                                        OcupacionLocalRepository ocupacionRepository,
                                        Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.clock = clock;
    }

    public Usuario usuarioActual(String email) {
        if (email == null) {
            throw new AccessDeniedException("Operacion no permitida");
        }
        return usuarioRepository.findByEmailAndActivoTrue(email)
            .orElseThrow(() -> new AccessDeniedException("Operacion no permitida"));
    }

    public boolean esGestion(Usuario usuario) {
        String rol = rol(usuario);
        return ROL_ADMIN.equals(rol) || ROL_EXPLOTACION.equals(rol);
    }

    // Subir, revisar y cambiar visibilidad: solo ADMIN y EXPLOTACION (403 para el resto).
    public void exigirGestion(Usuario usuario) {
        if (!esGestion(usuario)) {
            throw new AccessDeniedException("Operacion no permitida");
        }
    }

    // "Hoy" en la zona del reloj inyectado (Europe/Madrid).
    public LocalDate hoy() {
        return LocalDate.now(clock);
    }

    // Acceso a un local: gestion con alcance global provisional; INQUILINO solo con ocupacion propia vigente.
    public boolean puedeAccederLocal(Usuario usuario, Long localId) {
        if (esGestion(usuario)) {
            return true;
        }
        if (!ROL_INQUILINO.equals(rol(usuario))) {
            return false;
        }
        Inquilino inquilino = usuario.getInquilino();
        if (inquilino == null || !inquilino.isActivo()) {
            return false;
        }
        return ocupacionRepository.existeOcupacionVigente(localId, inquilino.getId(), hoy());
    }

    // FH-005A2-B: id del inquilino con el que filtrar listados. Vacio si el rol vigente no es INQUILINO
    // o si no tiene inquilino asociado y activo. La vigencia de la ocupacion la aplica cada consulta.
    public Optional<Long> inquilinoConAcceso(Usuario usuario) {
        if (!ROL_INQUILINO.equals(rol(usuario))) {
            return Optional.empty();
        }
        Inquilino inquilino = usuario.getInquilino();
        if (inquilino == null || !inquilino.isActivo()) {
            return Optional.empty();
        }
        return Optional.of(inquilino.getId());
    }

    // FH-006: solo ADMIN (alta y baja de proveedores y tecnicos, D-1).
    public boolean esAdmin(Usuario usuario) {
        return ROL_ADMIN.equals(rol(usuario));
    }

    public void exigirAdmin(Usuario usuario) {
        if (!esAdmin(usuario)) {
            throw new AccessDeniedException("Operacion no permitida");
        }
    }

    public boolean esProveedor(Usuario usuario) {
        return ROL_PROVEEDOR.equals(rol(usuario));
    }

    // FH-006: id de la empresa con la que filtrar trabajos. Vacio si el rol vigente no es PROVEEDOR
    // o si la empresa no existe o esta dada de baja (en ese caso el tecnico no ve ningun trabajo).
    public Optional<Long> proveedorConAcceso(Usuario usuario) {
        if (!esProveedor(usuario) || usuario.getProveedor() == null || !usuario.getProveedor().isActivo()) {
            return Optional.empty();
        }
        return Optional.of(usuario.getProveedor().getId());
    }

    // FH-006 / P6-C2: ocupacion vigente concreta del inquilino en el local (vacio si no la tiene o no es INQUILINO).
    public Optional<OcupacionLocal> ocupacionVigente(Usuario usuario, Long localId) {
        return inquilinoConAcceso(usuario)
            .flatMap(inquilinoId -> ocupacionRepository.ocupacionVigente(localId, inquilinoId, hoy()));
    }

    // FH-006 / P6-C2 (C8): proteccion del historial ante cambios de ocupacion. Un mantenimiento vinculado a una
    // ocupacion solo es de esa ocupacion; si no lo esta, su fecha del hecho debe caer dentro de la ocupacion.
    public static boolean cubreMantenimiento(OcupacionLocal ocupacion, Mantenimiento mantenimiento) {
        if (mantenimiento.getOcupacion() != null) {
            return mantenimiento.getOcupacion().getId().equals(ocupacion.getId());
        }
        LocalDate fecha = mantenimiento.fechaDelHecho();
        return !fecha.isBefore(ocupacion.getFechaInicio())
            && (ocupacion.getFechaFin() == null || !fecha.isAfter(ocupacion.getFechaFin()));
    }

    public boolean puedeLeerMantenimiento(Usuario usuario, Mantenimiento mantenimiento) {
        if (esGestion(usuario)) {
            return true;
        }
        // FH-006: obligaciones de zona comun (sin local) no dan acceso al inquilino.
        if (mantenimiento.getObligacion().getLocal() == null) {
            return false;
        }
        // Regla P5 (ocupacion propia vigente) y, ademas, que esa ocupacion cubra el mantenimiento (no el historial de
        // otro ocupante).
        Long localId = mantenimiento.getObligacion().getLocal().getId();
        return puedeAccederLocal(usuario, localId) && ocupacionVigente(usuario, localId)
            .map(ocupacion -> cubreMantenimiento(ocupacion, mantenimiento))
            .orElse(false);
    }

    public boolean puedeLeerDocumento(Usuario usuario, DocumentoMantenimiento documento) {
        if (esGestion(usuario)) {
            return true;
        }
        return visibleParaInquilino(documento) && puedeLeerMantenimiento(usuario, documento.getMantenimiento());
    }

    // FH-006 / P6-D3-2: regla unica de documento visible para el inquilino (descarga, cumplimiento e historial).
    public static boolean visibleParaInquilino(DocumentoMantenimiento documento) {
        return documento.getVisibilidad() == VisibilidadDocumento.COMPARTIDO
            && documento.getEstadoRevision() == EstadoRevisionDocumento.VALIDO;
    }

    private String rol(Usuario usuario) {
        return usuario.getRol() == null ? null : usuario.getRol().getCodigo();
    }
}
