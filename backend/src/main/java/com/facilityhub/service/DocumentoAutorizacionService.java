package com.facilityhub.service;

import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoRevisionDocumento;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.Mantenimiento;
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

    public boolean puedeLeerMantenimiento(Usuario usuario, Mantenimiento mantenimiento) {
        if (esGestion(usuario)) {
            return true;
        }
        return puedeAccederLocal(usuario, mantenimiento.getObligacion().getLocal().getId());
    }

    public boolean puedeLeerDocumento(Usuario usuario, DocumentoMantenimiento documento) {
        if (esGestion(usuario)) {
            return true;
        }
        return documento.getVisibilidad() == VisibilidadDocumento.COMPARTIDO
            && documento.getEstadoRevision() == EstadoRevisionDocumento.VALIDO
            && puedeLeerMantenimiento(usuario, documento.getMantenimiento());
    }

    private String rol(Usuario usuario) {
        return usuario.getRol() == null ? null : usuario.getRol().getCodigo();
    }
}
