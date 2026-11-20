package com.facilityhub.service;

import com.facilityhub.domain.AsignacionMantenimiento;
import com.facilityhub.domain.EstadoValidacion;
import com.facilityhub.domain.Intervencion;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.OrigenIntervencion;
import com.facilityhub.domain.ResponsableObligacion;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.AsignacionMantenimientoRepository;
import com.facilityhub.repository.IntervencionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

// FH-006: politica de acceso por rol, responsabilidad contractual y recurso para las operaciones P6.
// Se apoya en DocumentoAutorizacionService (comportamiento P5 sin cambios) y relee siempre el usuario en BD.
// Las acciones que devuelve son las unicas que Android muestra: la app no duplica reglas.
@Service
public class PoliticaAccesoService {
    public static final String ACCION_ASIGNAR_PROVEEDOR = "ASIGNAR_PROVEEDOR";
    public static final String ACCION_REGISTRAR_INTERVENCION = "REGISTRAR_INTERVENCION";
    public static final String ACCION_EDITAR_INTERVENCION = "EDITAR_INTERVENCION";
    public static final String ACCION_ADJUNTAR_DOCUMENTO = "ADJUNTAR_DOCUMENTO";
    public static final String ACCION_ENVIAR_INTERVENCION = "ENVIAR_INTERVENCION";
    public static final String ACCION_APROBAR = "APROBAR";
    public static final String ACCION_SOLICITAR_CORRECCION = "SOLICITAR_CORRECCION";
    public static final String ACCION_CERRAR_SIN_CUMPLIMIENTO = "CERRAR_SIN_CUMPLIMIENTO";

    static final EnumSet<EstadoValidacion> ABIERTAS =
        EnumSet.of(EstadoValidacion.BORRADOR, EstadoValidacion.PENDIENTE_VALIDACION, EstadoValidacion.REQUIERE_CORRECCION);

    private final DocumentoAutorizacionService autorizacion;
    private final IntervencionRepository intervencionRepository;
    private final AsignacionMantenimientoRepository asignacionRepository;

    public PoliticaAccesoService(DocumentoAutorizacionService autorizacion, IntervencionRepository intervencionRepository,
                                 AsignacionMantenimientoRepository asignacionRepository) {
        this.autorizacion = autorizacion;
        this.intervencionRepository = intervencionRepository;
        this.asignacionRepository = asignacionRepository;
    }

    // Asignar proveedor: ADMIN en cualquier obligacion; EXPLOTACION solo en las de responsabilidad NHOOD.
    // Las de responsabilidad INQUILINO las asigna el inquilino con su proveedor aceptado (P6-C2).
    public boolean puedeAsignar(Usuario usuario, ObligacionMantenimiento obligacion) {
        if (autorizacion.esAdmin(usuario)) {
            return true;
        }
        return autorizacion.esGestion(usuario) && obligacion.getResponsable() == ResponsableObligacion.NHOOD;
    }

    public void exigirAsignar(Usuario usuario, ObligacionMantenimiento obligacion) {
        if (!puedeAsignar(usuario, obligacion)) {
            throw new AccessDeniedException("Operacion no permitida");
        }
    }

    // ---------- Intervenciones (P6-C1) ----------

    // Asignacion activa del mantenimiento a la empresa del tecnico (vacio si no es PROVEEDOR o su empresa esta de baja).
    public Optional<AsignacionMantenimiento> asignacionActivaDelProveedor(Usuario usuario, Long mantenimientoId) {
        return autorizacion.proveedorConAcceso(usuario)
            .flatMap(proveedorId -> asignacionRepository.trabajoActivoDeProveedor(proveedorId, mantenimientoId));
    }

    // Lectura: gestion todas; PROVEEDOR solo las de su empresa (tambien tras terminar la asignacion: historico propio).
    public boolean puedeLeer(Usuario usuario, Intervencion intervencion) {
        if (autorizacion.esGestion(usuario)) {
            return true;
        }
        return autorizacion.proveedorConAcceso(usuario)
            .map(id -> id.equals(intervencion.getProveedor().getId()))
            .orElse(false);
    }

    // Edicion, adjuntos y envio: PROVEEDOR autor con la asignacion activa, o gestion en un registro externo (D-2).
    public boolean puedeEditar(Usuario usuario, Intervencion intervencion) {
        if (!intervencion.getEstadoValidacion().editable()) {
            return false;
        }
        if (intervencion.getOrigen() == OrigenIntervencion.REGISTRO_GESTION) {
            return autorizacion.esGestion(usuario);
        }
        return autorizacion.esProveedor(usuario)
            && puedeLeer(usuario, intervencion)
            && asignacionActivaDelProveedor(usuario, intervencion.getMantenimiento().getId()).isPresent();
    }

    // Validacion: solo gestion y solo sobre una intervencion presentada. Nunca implicita (D-6: puede ser el mismo
    // usuario que registro, en una accion separada y auditada).
    public boolean puedeValidar(Usuario usuario, Intervencion intervencion) {
        return autorizacion.esGestion(usuario) && intervencion.getEstadoValidacion() == EstadoValidacion.PENDIENTE_VALIDACION;
    }

    public List<String> accionesIntervencion(Usuario usuario, Intervencion intervencion) {
        List<String> acciones = new ArrayList<>();
        if (puedeEditar(usuario, intervencion)) {
            acciones.add(ACCION_EDITAR_INTERVENCION);
            acciones.add(ACCION_ADJUNTAR_DOCUMENTO);
            acciones.add(ACCION_ENVIAR_INTERVENCION);
        }
        if (puedeValidar(usuario, intervencion)) {
            if (intervencion.getResultado() != null && intervencion.getResultado().ejecutado()) {
                acciones.add(ACCION_APROBAR);
            }
            acciones.add(ACCION_SOLICITAR_CORRECCION);
            if (intervencion.getResultado() == com.facilityhub.domain.ResultadoIntervencion.NO_REALIZADO) {
                acciones.add(ACCION_CERRAR_SIN_CUMPLIMIENTO);
            }
        }
        return acciones;
    }

    // Admite el registro de una nueva intervencion: mantenimiento P6 (no historico P5), en un estado operativo abierto,
    // sin intervencion abierta ni aprobada.
    public boolean admiteNuevaIntervencion(Mantenimiento mantenimiento) {
        return mantenimiento.isValidacionRequerida()
            && mantenimiento.admiteAsignacion()
            && !intervencionRepository.existsByMantenimientoIdAndEstadoValidacionIn(mantenimiento.getId(),
                EnumSet.of(EstadoValidacion.BORRADOR, EstadoValidacion.PENDIENTE_VALIDACION,
                    EstadoValidacion.REQUIERE_CORRECCION, EstadoValidacion.APROBADO));
    }

    // Acciones del proveedor sobre un trabajo asignado (Android solo muestra estas).
    public List<String> accionesTrabajoProveedor(Usuario usuario, AsignacionMantenimiento asignacion) {
        Mantenimiento m = asignacion.getMantenimiento();
        Optional<Intervencion> abierta = intervencionRepository.findFirstByMantenimientoIdAndEstadoValidacionIn(m.getId(), ABIERTAS);
        if (abierta.isPresent()) {
            return puedeEditar(usuario, abierta.get())
                ? List.of(ACCION_EDITAR_INTERVENCION, ACCION_ADJUNTAR_DOCUMENTO, ACCION_ENVIAR_INTERVENCION)
                : List.of();
        }
        return admiteNuevaIntervencion(m) ? List.of(ACCION_REGISTRAR_INTERVENCION) : List.of();
    }

    public Optional<Intervencion> intervencionAbierta(Long mantenimientoId) {
        return intervencionRepository.findFirstByMantenimientoIdAndEstadoValidacionIn(mantenimientoId, ABIERTAS);
    }
}
