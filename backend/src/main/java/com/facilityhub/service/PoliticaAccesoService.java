package com.facilityhub.service;

import com.facilityhub.domain.AsignacionMantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.ResponsableObligacion;
import com.facilityhub.domain.Usuario;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

// FH-006: politica de acceso por rol, responsabilidad contractual y recurso para las operaciones P6.
// Se apoya en DocumentoAutorizacionService (comportamiento P5 sin cambios) y relee siempre el usuario en BD.
@Service
public class PoliticaAccesoService {
    public static final String ACCION_ASIGNAR_PROVEEDOR = "ASIGNAR_PROVEEDOR";

    private final DocumentoAutorizacionService autorizacion;

    public PoliticaAccesoService(DocumentoAutorizacionService autorizacion) {
        this.autorizacion = autorizacion;
    }

    // Asignar proveedor: ADMIN en cualquier obligacion; EXPLOTACION solo en las de responsabilidad NHOOD.
    // Las de responsabilidad INQUILINO las asigna el inquilino con su proveedor aceptado (P6-C).
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

    // Acciones que el proveedor puede realizar sobre un trabajo asignado. En P6-B el acceso es solo de consulta;
    // registrar, adjuntar y enviar llegan en P6-C. Android solo muestra las acciones listadas aqui.
    public List<String> accionesTrabajoProveedor(Usuario usuario, AsignacionMantenimiento asignacion) {
        return List.of();
    }
}
