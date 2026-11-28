package com.facilityhub.fh015;

import com.facilityhub.api.dto.inquilino.gestion.CambiarActivoInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.CrearInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.EditarInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.InquilinoGestionDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FH-015 / RF-05: reglas de la gestion de inquilinos (Mockito, sin BD). Datos sinteticos.
class InquilinosFH015UnitTest {
    // 23:30 UTC del 27/11 = 00:30 del 28/11 en Madrid: la fecha de los contadores es la de Madrid.
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-27T23:30:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 28);
    private static final String ADMIN = "admin@sintetico.test", EXPLOTACION = "explotacion@sintetico.test",
        INQUILINO = "inquilino@sintetico.test", PROVEEDOR = "proveedor@sintetico.test";

    private UsuarioRepository usuarioRepository;
    private InquilinoRepository inquilinoRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private MantenimientoRepository mantenimientoRepository;
    private SolicitudProveedorRepository solicitudRepository;
    private ObligacionProveedorRepository declaracionRepository;
    private AuditoriaService auditoria;
    private BloqueoCatalogo bloqueo;
    private DocumentoAutorizacionService autorizacion;
    private InquilinoGestionService servicio;
    private Inquilino inquilino;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        inquilinoRepository = mock(InquilinoRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        mantenimientoRepository = mock(MantenimientoRepository.class);
        solicitudRepository = mock(SolicitudProveedorRepository.class);
        declaracionRepository = mock(ObligacionProveedorRepository.class);
        auditoria = mock(AuditoriaService.class);
        bloqueo = mock(BloqueoCatalogo.class);
        autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        servicio = new InquilinoGestionService(inquilinoRepository, ocupacionRepository, mantenimientoRepository,
            usuarioRepository, solicitudRepository, declaracionRepository, autorizacion, auditoria, bloqueo, RELOJ);
        usuario(ADMIN, "ADMIN"); usuario(EXPLOTACION, "EXPLOTACION"); usuario(INQUILINO, "INQUILINO"); usuario(PROVEEDOR, "PROVEEDOR");
        inquilino = inquilino(70L, "B12345678", "Sintetica SL", "Tienda Sintetica");
        when(inquilinoRepository.findById(70L)).thenReturn(Optional.of(inquilino));
        when(inquilinoRepository.findParaGestion(70L)).thenReturn(Optional.of(inquilino));
        when(inquilinoRepository.findAllByOrderByRazonSocialAscIdAsc()).thenReturn(List.of(inquilino));
        when(inquilinoRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Inquilino i = inv.getArgument(0);
            ReflectionTestUtils.setField(i, "id", 99L);
            return i;
        });
        when(ocupacionRepository.countByInquilinoId(70L)).thenReturn(2L);
        when(usuarioRepository.countByInquilinoId(70L)).thenReturn(1L);
        when(solicitudRepository.countByInquilinoIdAndEstado(70L, EstadoSolicitudProveedor.PENDIENTE)).thenReturn(1L);
        when(declaracionRepository.contarActivasDelInquilino(70L)).thenReturn(3L);
    }

    @Test
    void altaNormalizaElCifBloqueaAntesDeComprobarYAudita() {
        InquilinoGestionDto dto = servicio.crear(new CrearInquilinoRequest(" b-87.654 321 ", "  Nueva SL ", " ",
            " info@nueva.test ", " +34 600-000-000 "), ADMIN);
        assertThat(dto.cif()).isEqualTo("B87654321");
        assertThat(dto.razonSocial()).isEqualTo("Nueva SL");
        assertThat(dto.nombreComercial()).isNull();
        assertThat(dto.emailContacto()).isEqualTo("info@nueva.test");
        assertThat(dto.telefono()).isEqualTo("+34 600-000-000");
        assertThat(dto.activo()).isTrue();
        assertThat(dto.huella()).hasSize(64);
        InOrder orden = inOrder(bloqueo, inquilinoRepository);
        orden.verify(bloqueo).bloquearInquilinos();
        orden.verify(inquilinoRepository).existeCif("B87654321", -1L);
        orden.verify(inquilinoRepository).saveAndFlush(any());
        verify(auditoria).registrar(eq("INQUILINO"), eq(99L), eq("CREAR"), any(), anyMap());
    }

    @Test
    void cifDuplicadoConOtroFormatoYCifNoValidoD2() {
        when(inquilinoRepository.existeCif("B12345678", -1L)).thenReturn(true);
        assertConflicto(() -> servicio.crear(peticion("b-1234 5678", "Otra SL"), ADMIN), "CIF_DUPLICADO");
        verify(inquilinoRepository, never()).saveAndFlush(any());
        for (String malo : new String[] {"   ", "B12", "B1234567890123456789X", "B1234_567", "ñ1234567"}) {
            assertThatThrownBy(() -> servicio.crear(peticion(malo, "X"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        }
        // Razon social repetida con otro CIF: se admite (la identificacion es el CIF).
        assertThat(servicio.crear(peticion("B99999999", "Sintetica SL"), ADMIN).razonSocial()).isEqualTo("Sintetica SL");
        assertThat(servicio.crear(peticion("A".repeat(20), "X"), ADMIN).cif()).hasSize(20);
    }

    @Test
    void permisosD1() {
        assertThat(servicio.listar(EXPLOTACION)).hasSize(1);
        assertThat(servicio.obtener(70L, EXPLOTACION).cif()).isEqualTo("B12345678");
        for (String email : new String[] {INQUILINO, PROVEEDOR}) {
            assertThatThrownBy(() -> servicio.listar(email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.obtener(70L, email)).isInstanceOf(AccessDeniedException.class);
        }
        for (String email : new String[] {EXPLOTACION, INQUILINO, PROVEEDOR}) {
            assertThatThrownBy(() -> servicio.crear(peticion("B11111111", "X"), email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.editar(70L, edicion(null, "X"), email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.cambiarActivo(70L, new CambiarActivoInquilinoRequest(false, huella(), true), email))
                .isInstanceOf(AccessDeniedException.class);
        }
        verify(inquilinoRepository, never()).saveAndFlush(any());
        verify(bloqueo, never()).bloquearInquilinos();
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void edicionConCifInmutableYHuellaD4D7() {
        // El CIF distinto se rechaza; el mismo CIF con otro formato se admite.
        assertThatThrownBy(() -> servicio.editar(70L, edicion("B00000000", "X"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThat(servicio.editar(70L, edicion(" b-12345678 ", "Sintetica Renovada SL"), ADMIN).razonSocial())
            .isEqualTo("Sintetica Renovada SL");
        assertThat(inquilino.getCif()).isEqualTo("B12345678");
        // Huella antigua (ya ha cambiado la razon social): 409 sin tocar nada.
        String antigua = "0".repeat(64);
        assertConflicto(() -> servicio.editar(70L, new EditarInquilinoRequest(null, "Otra", null, null, null, antigua), ADMIN),
            "DATOS_DESACTUALIZADOS");
        assertThat(inquilino.getRazonSocial()).isEqualTo("Sintetica Renovada SL");
        // Orden de bloqueo: catalogo y despues la fila.
        InOrder orden = inOrder(bloqueo, inquilinoRepository);
        orden.verify(bloqueo).bloquearInquilinos();
        orden.verify(inquilinoRepository).findParaGestion(70L);
        ArgumentCaptor<Map<String, Object>> detalle = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq("INQUILINO"), eq(70L), eq("EDITAR"), any(), detalle.capture());
        assertThat(detalle.getValue().get("razonSocialAnterior")).isEqualTo("Sintetica SL");
        assertThat(detalle.getValue().get("razonSocialNuevo")).isEqualTo("Sintetica Renovada SL");
    }

    @Test
    void edicionSinCambiosNoAuditaYCamposVaciosQuedanNulos() {
        servicio.editar(70L, new EditarInquilinoRequest(null, "Sintetica SL", "Tienda Sintetica", null, null, huella()), ADMIN);
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
        servicio.editar(70L, new EditarInquilinoRequest(null, "Sintetica SL", "  ", " ", " ", huella()), ADMIN);
        assertThat(inquilino.getNombreComercial()).isNull();
        verify(auditoria).registrar(eq("INQUILINO"), eq(70L), eq("EDITAR"), any(), anyMap());
    }

    @Test
    void registroAntiguoSinCifLoRecibeUnaSolaVezD4() {
        Inquilino antiguo = inquilino(71L, null, "Antigua SL", null);
        when(inquilinoRepository.findParaGestion(71L)).thenReturn(Optional.of(antiguo));
        // Se puede editar sin CIF (lectura tolerante).
        servicio.editar(71L, new EditarInquilinoRequest(null, "Antigua Renovada SL", null, null, null,
            InquilinoGestionService.huella(antiguo)), ADMIN);
        assertThat(antiguo.getCif()).isNull();
        // CIF ya usado por otro: 409.
        when(inquilinoRepository.existeCif("B12345678", 71L)).thenReturn(true);
        assertConflicto(() -> servicio.editar(71L, new EditarInquilinoRequest("B12345678", "Antigua Renovada SL", null,
            null, null, InquilinoGestionService.huella(antiguo)), ADMIN), "CIF_DUPLICADO");
        assertThat(antiguo.getCif()).isNull();
        // CIF libre: se asigna y queda auditado.
        servicio.editar(71L, new EditarInquilinoRequest("c-7654321", "Antigua Renovada SL", null, null, null,
            InquilinoGestionService.huella(antiguo)), ADMIN);
        assertThat(antiguo.getCif()).isEqualTo("C7654321");
        // A partir de ahi es inmutable.
        assertThatThrownBy(() -> servicio.editar(71L, new EditarInquilinoRequest("C0000000", "Antigua Renovada SL", null,
            null, null, InquilinoGestionService.huella(antiguo)), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> antiguo.asignarCif("X1111111")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void desactivacionBloqueadaPorOcupacionesYMantenimientosVinculadosAunqueSeConfirmeD5() {
        when(ocupacionRepository.contarVigentesDelInquilino(70L, HOY)).thenReturn(1L);
        assertConflicto(() -> servicio.cambiarActivo(70L, new CambiarActivoInquilinoRequest(false, huella(), true), ADMIN),
            "INQUILINO_EN_USO");
        when(ocupacionRepository.contarVigentesDelInquilino(70L, HOY)).thenReturn(0L);
        when(ocupacionRepository.contarFuturasDelInquilino(70L, HOY)).thenReturn(1L);
        assertConflicto(() -> servicio.cambiarActivo(70L, new CambiarActivoInquilinoRequest(false, huella(), true), ADMIN),
            "INQUILINO_EN_USO");
        when(ocupacionRepository.contarFuturasDelInquilino(70L, HOY)).thenReturn(0L);
        when(mantenimientoRepository.contarAbiertosDelInquilino(70L)).thenReturn(1L);
        assertConflicto(() -> servicio.cambiarActivo(70L, new CambiarActivoInquilinoRequest(false, huella(), true), ADMIN),
            "INQUILINO_EN_USO");
        assertThat(inquilino.isActivo()).isTrue();
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void desactivacionExigeConfirmacionConservaTodoYReactivacionD5D6() {
        assertThatThrownBy(() -> servicio.cambiarActivo(70L, new CambiarActivoInquilinoRequest(false, huella(), null), ADMIN))
            .isInstanceOf(ConflictoCodificadoException.class)
            .satisfies(e -> {
                ConflictoCodificadoException c = (ConflictoCodificadoException) e;
                assertThat(c.getCodigo()).isEqualTo("IMPACTO_NO_CONFIRMADO");
                assertThat(c.getDatos()).containsEntry("ocupaciones", 2L).containsEntry("usuarios", 1L)
                    .containsEntry("solicitudesPendientes", 1L).containsEntry("declaracionesActivas", 3L);
            });
        assertThat(inquilino.isActivo()).isTrue();
        InquilinoGestionDto dto = servicio.cambiarActivo(70L, new CambiarActivoInquilinoRequest(false, huella(), true), ADMIN);
        assertThat(dto.activo()).isFalse();
        // Sin cascada: solo cambia el inquilino (no se guarda ni borra nada mas).
        verify(inquilinoRepository, never()).saveAndFlush(any());
        verify(inquilinoRepository, never()).delete(any());
        verify(ocupacionRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
        verify(solicitudRepository, never()).save(any());
        verify(declaracionRepository, never()).save(any());
        verify(auditoria).registrar(eq("INQUILINO"), eq(70L), eq("DESACTIVAR"), any(), anyMap());
        // Reactivar siempre se permite y queda auditado.
        assertThat(servicio.cambiarActivo(70L, new CambiarActivoInquilinoRequest(true, huella(), null), ADMIN).activo()).isTrue();
        verify(auditoria).registrar(eq("INQUILINO"), eq(70L), eq("REACTIVAR"), any(), anyMap());
    }

    @Test
    void contadoresConLaFechaDeMadrid() {
        servicio.obtener(70L, ADMIN);
        verify(ocupacionRepository).contarVigentesDelInquilino(70L, HOY);
        verify(ocupacionRepository).contarFuturasDelInquilino(70L, HOY);
        verify(mantenimientoRepository).contarAbiertosDelInquilino(70L);
    }

    @Test
    void inquilinoInactivoNoDaAccesoAlAreaDelInquilino() {
        // Regla existente que se conserva: los usuarios de un inquilino inactivo pierden el acceso (sin tocar el usuario).
        Usuario u = mock(Usuario.class);
        Rol r = mock(Rol.class);
        when(r.getCodigo()).thenReturn("INQUILINO");
        when(u.getRol()).thenReturn(r);
        when(u.getInquilino()).thenReturn(inquilino);
        assertThat(autorizacion.inquilinoConAcceso(u)).contains(70L);
        inquilino.cambiarActivo(false);
        assertThat(autorizacion.inquilinoConAcceso(u)).isEmpty();
    }

    private void assertConflicto(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOf(ConflictoCodificadoException.class)
            .satisfies(e -> assertThat(((ConflictoCodificadoException) e).getCodigo()).isEqualTo(codigo));
    }

    private String huella() { return InquilinoGestionService.huella(inquilino); }

    private EditarInquilinoRequest edicion(String cif, String razonSocial) {
        return new EditarInquilinoRequest(cif, razonSocial, inquilino.getNombreComercial(), inquilino.getEmailContacto(),
            inquilino.getTelefono(), huella());
    }

    private static CrearInquilinoRequest peticion(String cif, String razonSocial) {
        return new CrearInquilinoRequest(cif, razonSocial, null, null, null);
    }

    private static Inquilino inquilino(Long id, String cif, String razonSocial, String nombreComercial) {
        Inquilino i = new Inquilino(cif, razonSocial, nombreComercial, null, null, LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    private void usuario(String email, String codigoRol) {
        Usuario u = mock(Usuario.class);
        Rol r = mock(Rol.class);
        when(r.getCodigo()).thenReturn(codigoRol);
        when(u.getRol()).thenReturn(r);
        when(u.getEmail()).thenReturn(email);
        when(usuarioRepository.findByEmailAndActivoTrue(email)).thenReturn(Optional.of(u));
    }
}
