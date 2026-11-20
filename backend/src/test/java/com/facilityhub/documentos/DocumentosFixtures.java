package com.facilityhub.documentos;

import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoRevisionDocumento;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.LocalComercial;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.Rol;
import com.facilityhub.domain.Usuario;
import com.facilityhub.domain.VisibilidadDocumento;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Identidades y registros sinteticos para las pruebas FH-005A1. No usan BD ni datos reales.
final class DocumentosFixtures {
    static final Long LOCAL_PROPIO = 901L;
    static final Long LOCAL_AJENO = 902L;
    static final Long INQUILINO_ID = 801L;
    static final Long MANTENIMIENTO_PROPIO = 7001L;
    static final Long MANTENIMIENTO_AJENO = 7002L;

    // 23:30 UTC del 16/11 ya es 17/11 en Europe/Madrid: fija "hoy" y comprueba la zona.
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-16T23:30:00Z"), ZoneId.of("Europe/Madrid"));
    static final LocalDate HOY = LocalDate.of(2026, 11, 17);

    private DocumentosFixtures() {}

    static Usuario usuario(String email, String rol, Inquilino inquilino) {
        Usuario usuario = mock(Usuario.class);
        Rol codigoRol = mock(Rol.class);
        when(codigoRol.getCodigo()).thenReturn(rol);
        when(usuario.getEmail()).thenReturn(email);
        when(usuario.getRol()).thenReturn(codigoRol);
        when(usuario.getInquilino()).thenReturn(inquilino);
        when(usuario.isActivo()).thenReturn(true);
        return usuario;
    }

    static Inquilino inquilino(boolean activo) {
        Inquilino inquilino = mock(Inquilino.class);
        when(inquilino.getId()).thenReturn(INQUILINO_ID);
        when(inquilino.isActivo()).thenReturn(activo);
        return inquilino;
    }

    static Mantenimiento mantenimiento(Long mantenimientoId, Long localId) {
        LocalComercial local = mock(LocalComercial.class);
        when(local.getId()).thenReturn(localId);
        ObligacionMantenimiento obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getLocal()).thenReturn(local);
        Mantenimiento mantenimiento = mock(Mantenimiento.class);
        when(mantenimiento.getId()).thenReturn(mantenimientoId);
        when(mantenimiento.getObligacion()).thenReturn(obligacion);
        // P6-C2: hecho dentro de la ocupacion vigente sintetica (sin ocupacion vinculada).
        when(mantenimiento.fechaDelHecho()).thenReturn(HOY);
        return mantenimiento;
    }

    // P6-C2: ocupacion vigente sintetica del inquilino en un local (iniciada antes de HOY, sin fin).
    static com.facilityhub.domain.OcupacionLocal ocupacionVigente(Long ocupacionId) {
        com.facilityhub.domain.OcupacionLocal ocupacion = mock(com.facilityhub.domain.OcupacionLocal.class);
        when(ocupacion.getId()).thenReturn(ocupacionId);
        when(ocupacion.getFechaInicio()).thenReturn(HOY.minusYears(1));
        when(ocupacion.isActivo()).thenReturn(true);
        return ocupacion;
    }

    static DocumentoMantenimiento documento(Mantenimiento mantenimiento, EstadoRevisionDocumento estado,
                                            VisibilidadDocumento visibilidad) {
        DocumentoMantenimiento documento = new DocumentoMantenimiento(
            mantenimiento, "SINTETICO-prueba.pdf", "/almacen/sintetico/prueba.pdf", null, null, null);
        documento.revisar(estado, "Observacion interna sintetica");
        if (visibilidad == VisibilidadDocumento.COMPARTIDO) {
            documento.cambiarVisibilidad(VisibilidadDocumento.COMPARTIDO);
        }
        return documento;
    }
}
