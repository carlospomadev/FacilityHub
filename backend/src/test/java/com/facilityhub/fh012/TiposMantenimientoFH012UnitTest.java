package com.facilityhub.fh012;

import com.facilityhub.api.dto.tipo.CambiarActivoTipoRequest;
import com.facilityhub.api.dto.tipo.CrearTipoRequest;
import com.facilityhub.api.dto.tipo.EditarTipoRequest;
import com.facilityhub.api.dto.tipo.TipoGestionDto;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FH-012 / RF-08: reglas de la gestion del catalogo de tipos (Mockito, sin BD). Datos sinteticos.
class TiposMantenimientoFH012UnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final String ADMIN = "admin@sintetico.test", EXPLOTACION = "explotacion@sintetico.test",
        INQUILINO = "inquilino@sintetico.test", PROVEEDOR = "proveedor@sintetico.test";

    private UsuarioRepository usuarioRepository;
    private TipoMantenimientoRepository tipoRepository;
    private ObligacionMantenimientoRepository obligacionRepository;
    private IntervencionRepository intervencionRepository;
    private AuditoriaService auditoria;
    private BloqueoCatalogo bloqueo;
    private TipoMantenimientoService servicio;
    private TipoMantenimiento tipo;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        tipoRepository = mock(TipoMantenimientoRepository.class);
        obligacionRepository = mock(ObligacionMantenimientoRepository.class);
        intervencionRepository = mock(IntervencionRepository.class);
        auditoria = mock(AuditoriaService.class);
        bloqueo = mock(BloqueoCatalogo.class);
        DocumentoAutorizacionService autorizacion =
            new DocumentoAutorizacionService(usuarioRepository, mock(OcupacionLocalRepository.class), RELOJ);
        servicio = new TipoMantenimientoService(tipoRepository, obligacionRepository, intervencionRepository, autorizacion,
            auditoria, bloqueo);
        usuario(ADMIN, "ADMIN"); usuario(EXPLOTACION, "EXPLOTACION"); usuario(INQUILINO, "INQUILINO"); usuario(PROVEEDOR, "PROVEEDOR");
        tipo = tipo(7L, "CLIMA-SEM", "Climatizacion semestral", 6, false);
        when(tipoRepository.findParaEditar(7L)).thenReturn(Optional.of(tipo));
        when(tipoRepository.findById(7L)).thenReturn(Optional.of(tipo));
        when(tipoRepository.findAll()).thenReturn(List.of(tipo, tipo(8L, "BT-ANUAL", "Revisión eléctrica anual", 12, true)));
        when(tipoRepository.saveAndFlush(any())).thenAnswer(inv -> {
            TipoMantenimiento t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 99L);
            return t;
        });
    }

    @Test
    void altaNormalizaElCodigoBloqueaElCatalogoYAudita() {
        TipoGestionDto dto = servicio.crear(new CrearTipoRequest(" pci-mens ", "  Revision PCI mensual ", " ", 1, true,
            "DESDE_PROGRAMADA"), ADMIN);
        assertThat(dto.codigo()).isEqualTo("PCI-MENS");
        assertThat(dto.nombre()).isEqualTo("Revision PCI mensual");
        assertThat(dto.descripcion()).isNull();
        assertThat(dto.activo()).isTrue();
        assertThat(dto.huella()).hasSize(64);
        InOrder orden = inOrder(bloqueo, tipoRepository);
        orden.verify(bloqueo).bloquear();
        orden.verify(tipoRepository).existsByCodigo("PCI-MENS");
        verify(auditoria).registrar(eq("TIPO_MANTENIMIENTO"), eq(99L), eq("CREAR"), any(), anyMap());
    }

    @Test
    void codigoNoValidoYDuplicados() {
        assertThatThrownBy(() -> servicio.crear(peticion("A", "Nuevo"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.crear(peticion("CON ESPACIO", "Nuevo"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.crear(peticion("ÑANDU", "Nuevo"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        when(tipoRepository.existsByCodigo("CLIMA-SEM")).thenReturn(true);
        assertConflicto(() -> servicio.crear(peticion("clima-sem", "Otro"), ADMIN), "CODIGO_DUPLICADO");
        // Nombre igual sin distinguir mayusculas, espacios exteriores ni tildes en mayuscula.
        assertConflicto(() -> servicio.crear(peticion("NUEVO-1", "  REVISIÓN ELÉCTRICA ANUAL "), ADMIN), "NOMBRE_DUPLICADO");
        verify(tipoRepository, never()).saveAndFlush(any());
    }

    @Test
    void permisosD1() {
        assertThat(servicio.listarCatalogo(EXPLOTACION)).isNotNull();
        assertThat(servicio.obtener(7L, EXPLOTACION).codigo()).isEqualTo("CLIMA-SEM");
        for (String email : new String[] {EXPLOTACION, INQUILINO, PROVEEDOR}) {
            assertThatThrownBy(() -> servicio.crear(peticion("X-1", "X"), email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.editar(7L, edicion("Climatizacion semestral", 6, false, null), email))
                .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.cambiarActivo(7L, new CambiarActivoTipoRequest(false, huella(), true), email))
                .isInstanceOf(AccessDeniedException.class);
        }
        for (String email : new String[] {INQUILINO, PROVEEDOR}) {
            assertThatThrownBy(() -> servicio.listarCatalogo(email)).isInstanceOf(AccessDeniedException.class);
        }
        verify(bloqueo, never()).bloquear();
    }

    @Test
    void codigoInmutableYHuellaDesactualizada() {
        EditarTipoRequest otroCodigo = new EditarTipoRequest("OTRO", "Climatizacion semestral", null, 6, false,
            "DESDE_EJECUCION", huella(), null);
        assertThatThrownBy(() -> servicio.editar(7L, otroCodigo, ADMIN)).isInstanceOf(IllegalArgumentException.class);
        EditarTipoRequest mismoCodigo = new EditarTipoRequest("clima-sem", "Climatizacion semestral (nueva)", null, 6, false,
            "DESDE_EJECUCION", huella(), null);
        assertThat(servicio.editar(7L, mismoCodigo, ADMIN).nombre()).isEqualTo("Climatizacion semestral (nueva)");
        // La huella anterior ya no vale: otro guardado con ella se rechaza sin cambiar nada.
        EditarTipoRequest viejo = new EditarTipoRequest(null, "Tercer nombre", null, 6, false, "DESDE_EJECUCION",
            "huella-antigua", null);
        assertConflicto(() -> servicio.editar(7L, viejo, ADMIN), "DATOS_DESACTUALIZADOS");
        assertThat(tipo.getNombre()).isEqualTo("Climatizacion semestral (nueva)");
    }

    @Test
    void periodicidadConUsoExigeConfirmacionYSoloCambiaElTipo() {
        when(obligacionRepository.countByTipoMantenimientoIdAndActivoTrue(7L)).thenReturn(3L);
        assertConflicto(() -> servicio.editar(7L, edicion("Climatizacion semestral", 12, false, null), ADMIN), "IMPACTO_NO_CONFIRMADO");
        assertThat(tipo.getPeriodicidadMeses()).isEqualTo(6);
        assertConflicto(() -> servicio.editar(7L, new EditarTipoRequest(null, "Climatizacion semestral", null, 6, false,
            "DESDE_PROGRAMADA", huella(), false), ADMIN), "IMPACTO_NO_CONFIRMADO");
        TipoGestionDto dto = servicio.editar(7L, edicion("Climatizacion semestral", 12, false, true), ADMIN);
        assertThat(dto.periodicidadMeses()).isEqualTo(12);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detalle = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq("TIPO_MANTENIMIENTO"), eq(7L), eq("EDITAR"), any(), detalle.capture());
        assertThat((Map<String, Object>) detalle.getValue()).containsEntry("periodicidadMesesAnterior", 6).containsEntry("periodicidadMesesNuevo", 12)
            .containsEntry("obligacionesActivas", 3L).containsEntry("impactoConfirmado", true);
        verifyNoMoreInteractions(intervencionRepository);
    }

    @Test
    void sinUsoNoHaceFaltaConfirmarYSinCambiosNoSeAudita() {
        when(obligacionRepository.countByTipoMantenimientoIdAndActivoTrue(7L)).thenReturn(0L);
        assertThat(servicio.editar(7L, edicion("Climatizacion semestral", 6, false, null), ADMIN).periodicidadMeses()).isEqualTo(6);
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
        assertThat(servicio.editar(7L, edicion("Climatizacion semestral", 4, false, null), ADMIN).periodicidadMeses()).isEqualTo(4);
        verify(auditoria).registrar(eq("TIPO_MANTENIMIENTO"), eq(7L), eq("EDITAR"), any(), anyMap());
    }

    @Test
    void exigirDocumentoConIntervencionesAbiertasSinEvidenciaSeRechazaAunqueSeConfirme() {
        when(obligacionRepository.countByTipoMantenimientoIdAndActivoTrue(7L)).thenReturn(2L);
        when(intervencionRepository.contarAbiertasSinDocumento(7L)).thenReturn(1L);
        assertConflicto(() -> servicio.editar(7L, edicion("Climatizacion semestral", 6, true, true), ADMIN),
            "INTERVENCIONES_SIN_DOCUMENTO");
        assertThat(tipo.isRequiereDocumento()).isFalse();
        when(intervencionRepository.contarAbiertasSinDocumento(7L)).thenReturn(0L);
        assertThat(servicio.editar(7L, edicion("Climatizacion semestral", 6, true, true), ADMIN).requiereDocumento()).isTrue();
    }

    @Test
    void desactivarConUsoExigeConfirmacionYReactivar() {
        when(obligacionRepository.countByTipoMantenimientoIdAndActivoTrue(7L)).thenReturn(5L);
        assertConflicto(() -> servicio.cambiarActivo(7L, new CambiarActivoTipoRequest(false, huella(), null), ADMIN),
            "IMPACTO_NO_CONFIRMADO");
        assertThat(tipo.isActivo()).isTrue();
        assertThat(servicio.cambiarActivo(7L, new CambiarActivoTipoRequest(false, huella(), true), ADMIN).activo()).isFalse();
        verify(auditoria).registrar(eq("TIPO_MANTENIMIENTO"), eq(7L), eq("DESACTIVAR"), any(), anyMap());
        // La huella antigua (activo) ya no sirve; con la nueva se reactiva.
        assertConflicto(() -> servicio.cambiarActivo(7L, new CambiarActivoTipoRequest(true, "x", null), ADMIN), "DATOS_DESACTUALIZADOS");
        assertThat(servicio.cambiarActivo(7L, new CambiarActivoTipoRequest(true, huella(), null), ADMIN).activo()).isTrue();
        verify(auditoria).registrar(eq("TIPO_MANTENIMIENTO"), eq(7L), eq("REACTIVAR"), any(), anyMap());
        verify(obligacionRepository, never()).save(any());
    }

    private void assertConflicto(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOf(ConflictoCodificadoException.class)
            .satisfies(e -> assertThat(((ConflictoCodificadoException) e).getCodigo()).isEqualTo(codigo));
    }

    private String huella() { return TipoMantenimientoService.huella(tipo); }

    private EditarTipoRequest edicion(String nombre, int meses, boolean documento, Boolean confirmar) {
        return new EditarTipoRequest(null, nombre, null, meses, documento, "DESDE_EJECUCION", huella(), confirmar);
    }

    private static CrearTipoRequest peticion(String codigo, String nombre) {
        return new CrearTipoRequest(codigo, nombre, null, 3, true, "DESDE_EJECUCION");
    }

    private static TipoMantenimiento tipo(Long id, String codigo, String nombre, int meses, boolean documento) {
        TipoMantenimiento t = new TipoMantenimiento(codigo, nombre, null, meses, documento, ReglaRecalculo.DESDE_EJECUCION);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
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
