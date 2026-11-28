package com.facilityhub.fh014;

import com.facilityhub.api.dto.CrearMantenimientoRequest;
import com.facilityhub.api.dto.local.CambiarActivoLocalRequest;
import com.facilityhub.api.dto.local.CrearLocalRequest;
import com.facilityhub.api.dto.local.EditarLocalRequest;
import com.facilityhub.api.dto.local.LocalGestionDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
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

// FH-014 / RF-04: reglas de la gestion de locales (Mockito, sin BD). Datos sinteticos.
class LocalesFH014UnitTest {
    // 23:30 UTC del 27/11 = 00:30 del 28/11 en Madrid: la fecha de los contadores es la de Madrid.
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-27T23:30:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 28);
    private static final String ADMIN = "admin@sintetico.test", EXPLOTACION = "explotacion@sintetico.test",
        INQUILINO = "inquilino@sintetico.test", PROVEEDOR = "proveedor@sintetico.test";

    private UsuarioRepository usuarioRepository;
    private CentroRepository centroRepository;
    private LocalComercialRepository localRepository;
    private InstalacionRepository instalacionRepository;
    private ObligacionMantenimientoRepository obligacionRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private MantenimientoRepository mantenimientoRepository;
    private AuditoriaService auditoria;
    private DocumentoAutorizacionService autorizacion;
    private LocalGestionService servicio;
    private Centro centro;
    private LocalComercial local;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        centroRepository = mock(CentroRepository.class);
        localRepository = mock(LocalComercialRepository.class);
        instalacionRepository = mock(InstalacionRepository.class);
        obligacionRepository = mock(ObligacionMantenimientoRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        mantenimientoRepository = mock(MantenimientoRepository.class);
        auditoria = mock(AuditoriaService.class);
        autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        servicio = new LocalGestionService(centroRepository, localRepository, instalacionRepository, obligacionRepository,
            ocupacionRepository, mantenimientoRepository, autorizacion, auditoria, RELOJ);
        usuario(ADMIN, "ADMIN"); usuario(EXPLOTACION, "EXPLOTACION"); usuario(INQUILINO, "INQUILINO"); usuario(PROVEEDOR, "PROVEEDOR");
        centro = centro(5L, true);
        local = local(50L, centro, "L-101", "Restaurante", "Planta 1", null);
        when(centroRepository.existsById(5L)).thenReturn(true);
        when(centroRepository.findParaAlta(5L)).thenReturn(Optional.of(centro));
        when(localRepository.findById(50L)).thenReturn(Optional.of(local));
        when(localRepository.centroIdDe(50L)).thenReturn(Optional.of(5L));
        when(localRepository.findParaAlta(50L)).thenReturn(Optional.of(local));
        when(localRepository.findByCentroIdOrderByCodigoAsc(5L)).thenReturn(List.of(local));
        when(localRepository.saveAndFlush(any())).thenAnswer(inv -> {
            LocalComercial l = inv.getArgument(0);
            ReflectionTestUtils.setField(l, "id", 99L);
            return l;
        });
        when(instalacionRepository.countByLocalId(50L)).thenReturn(2L);
        when(obligacionRepository.countByLocalIdAndActivoTrue(50L)).thenReturn(3L);
        when(mantenimientoRepository.contarDelLocal(50L)).thenReturn(7L);
        when(ocupacionRepository.countByLocalId(50L)).thenReturn(1L);
    }

    @Test
    void altaNormalizaElCodigoBloqueaElCentroYAudita() {
        LocalGestionDto dto = servicio.crear(5L, new CrearLocalRequest("  l-300 ", "  Tienda Nueva ", " ", " Galeria "), ADMIN);
        assertThat(dto.codigo()).isEqualTo("L-300");
        assertThat(dto.nombre()).isEqualTo("Tienda Nueva");
        assertThat(dto.planta()).isNull();
        assertThat(dto.ubicacion()).isEqualTo("Galeria");
        assertThat(dto.centroId()).isEqualTo(5L);
        assertThat(dto.activo()).isTrue();
        assertThat(dto.huella()).hasSize(64);
        InOrder orden = inOrder(centroRepository, localRepository);
        orden.verify(centroRepository).findParaAlta(5L);
        orden.verify(localRepository).existsByCentroIdAndCodigoIgnoreCase(5L, "L-300");
        orden.verify(localRepository).saveAndFlush(any());
        verify(auditoria).registrar(eq("LOCAL"), eq(99L), eq("CREAR"), any(), anyMap());
    }

    @Test
    void codigoDuplicadoEnElCentroPeroNoEnOtroD2() {
        when(localRepository.existsByCentroIdAndCodigoIgnoreCase(5L, "L-101")).thenReturn(true);
        assertConflicto(() -> servicio.crear(5L, peticion(" l-101 ", "Otro"), ADMIN), "CODIGO_DUPLICADO");
        verify(localRepository, never()).saveAndFlush(any());
        // El mismo codigo en otro centro si se admite (la unicidad es por centro).
        Centro otro = centro(6L, true);
        when(centroRepository.findParaAlta(6L)).thenReturn(Optional.of(otro));
        assertThat(servicio.crear(6L, peticion("L-101", "Otro"), ADMIN).centroId()).isEqualTo(6L);
        // Ultima defensa: el indice uq_local_centro_codigo se traduce al mismo 409.
        doThrow(new DataIntegrityViolationException("uq_local_centro_codigo")).when(localRepository).saveAndFlush(any());
        assertConflicto(() -> servicio.crear(6L, peticion("L-102", "Otro"), ADMIN), "CODIGO_DUPLICADO");
    }

    @Test
    void codigoNoValidoYNombreRepetidoD2D9() {
        assertThatThrownBy(() -> servicio.crear(5L, peticion("   ", "X"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.crear(5L, peticion("A".repeat(41), "X"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThat(servicio.crear(5L, peticion("A".repeat(40), "X"), ADMIN).codigo()).hasSize(40);
        // El nombre puede repetirse: la identificacion es centro + codigo.
        assertThat(servicio.crear(5L, peticion("L-400", "Restaurante"), ADMIN).nombre()).isEqualTo("Restaurante");
    }

    @Test
    void centroInactivoNoAdmiteAltaNiReactivacionPeroSiEdicionYDesactivacionD5() {
        centro.cambiarActivo(false);
        assertConflicto(() -> servicio.crear(5L, peticion("L-500", "X"), ADMIN), "CENTRO_INACTIVO");
        // Edicion permitida.
        assertThat(servicio.editar(50L, edicion("Restaurante Renovado"), ADMIN).nombre()).isEqualTo("Restaurante Renovado");
        // Desactivacion permitida (con confirmacion).
        assertThat(servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(false, huella(), true), ADMIN).activo()).isFalse();
        // Reactivacion rechazada mientras el centro este inactivo.
        assertConflicto(() -> servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(true, huella(), null), ADMIN),
            "CENTRO_INACTIVO");
        assertThat(local.isActivo()).isFalse();
    }

    @Test
    void permisosD1() {
        assertThat(servicio.listar(5L, EXPLOTACION)).hasSize(1);
        assertThat(servicio.obtener(50L, EXPLOTACION).codigo()).isEqualTo("L-101");
        for (String email : new String[] {INQUILINO, PROVEEDOR}) {
            assertThatThrownBy(() -> servicio.listar(5L, email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.obtener(50L, email)).isInstanceOf(AccessDeniedException.class);
        }
        for (String email : new String[] {EXPLOTACION, INQUILINO, PROVEEDOR}) {
            assertThatThrownBy(() -> servicio.crear(5L, peticion("X-1", "X"), email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.editar(50L, edicion("X"), email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(false, huella(), true), email))
                .isInstanceOf(AccessDeniedException.class);
        }
        verify(localRepository, never()).saveAndFlush(any());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void edicionNoCambiaCentroNiCodigoYUsaHuellaD2D6D7() {
        assertThatThrownBy(() -> servicio.editar(50L, new EditarLocalRequest(null, 6L, "X", null, null, huella()), ADMIN))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("centro");
        assertThatThrownBy(() -> servicio.editar(50L, new EditarLocalRequest("L-999", null, "X", null, null, huella()), ADMIN))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("codigo");
        // Mismo centro y mismo codigo (en minusculas) si se admiten.
        assertThat(servicio.editar(50L, new EditarLocalRequest(" l-101", 5L, "Restaurante", "Planta 2", null, huella()), ADMIN)
            .planta()).isEqualTo("Planta 2");
        // Huella antigua: 409 sin sobrescribir.
        assertConflicto(() -> servicio.editar(50L, new EditarLocalRequest(null, null, "Pisado", null, null, "antigua"), ADMIN),
            "DATOS_DESACTUALIZADOS");
        assertThat(local.getNombre()).isEqualTo("Restaurante");
        assertThat(local.getCodigo()).isEqualTo("L-101");
        assertThat(local.getCentro().getId()).isEqualTo(5L);
        // Orden de bloqueo fijo: centro y despues local.
        InOrder orden = inOrder(centroRepository, localRepository);
        orden.verify(centroRepository).findParaAlta(5L);
        orden.verify(localRepository).findParaAlta(50L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> detalle = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq("LOCAL"), eq(50L), eq("EDITAR"), any(), detalle.capture());
        Map<String, ?> valores = detalle.getValue();
        assertThat(valores.get("plantaAnterior")).isEqualTo("Planta 1");
        assertThat(valores.get("plantaNuevo")).isEqualTo("Planta 2");
    }

    @Test
    void desactivacionBloqueadaPorOcupacionesYMantenimientosAunqueSeConfirmeD4() {
        when(ocupacionRepository.contarVigentesDelLocal(50L, HOY)).thenReturn(1L);
        assertConflicto(() -> servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(false, huella(), true), ADMIN), "LOCAL_EN_USO");
        when(ocupacionRepository.contarVigentesDelLocal(50L, HOY)).thenReturn(0L);
        when(ocupacionRepository.contarFuturasDelLocal(50L, HOY)).thenReturn(1L);
        assertConflicto(() -> servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(false, huella(), true), ADMIN), "LOCAL_EN_USO");
        when(ocupacionRepository.contarFuturasDelLocal(50L, HOY)).thenReturn(0L);
        when(mantenimientoRepository.contarAbiertosDelLocal(50L)).thenReturn(2L);
        assertThatThrownBy(() -> servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(false, huella(), true), ADMIN))
            .isInstanceOf(ConflictoCodificadoException.class)
            .satisfies(e -> assertThat(((ConflictoCodificadoException) e).getDatos()).containsEntry("mantenimientosAbiertos", 2L));
        assertThat(local.isActivo()).isTrue();
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void desactivacionExigeConfirmacionYConservaTodoD4D12() {
        assertThatThrownBy(() -> servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(false, huella(), null), ADMIN))
            .isInstanceOf(ConflictoCodificadoException.class)
            .satisfies(e -> assertThat(((ConflictoCodificadoException) e).getDatos())
                .containsEntry("instalaciones", 2L).containsEntry("obligacionesActivas", 3L).containsEntry("mantenimientos", 7L));
        assertThat(local.isActivo()).isTrue();
        LocalGestionDto dto = servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(false, huella(), true), ADMIN);
        assertThat(dto.activo()).isFalse();
        assertThat(dto.instalaciones()).isEqualTo(2L);
        verify(auditoria).registrar(eq("LOCAL"), eq(50L), eq("DESACTIVAR"), any(), anyMap());
        // Sin cascada ni borrados.
        verify(localRepository, never()).delete(any());
        verify(instalacionRepository, never()).save(any());
        verify(obligacionRepository, never()).save(any());
        verify(ocupacionRepository, never()).save(any());
        verify(mantenimientoRepository, never()).save(any());
        // Reactivar con el centro activo.
        assertThat(servicio.cambiarActivo(50L, new CambiarActivoLocalRequest(true, huella(), null), ADMIN).activo()).isTrue();
        verify(auditoria).registrar(eq("LOCAL"), eq(50L), eq("REACTIVAR"), any(), anyMap());
    }

    @Test
    void contadoresConLaFechaDeMadrid() {
        when(ocupacionRepository.contarVigentesDelLocal(50L, HOY)).thenReturn(1L);
        when(ocupacionRepository.contarFuturasDelLocal(50L, HOY)).thenReturn(1L);
        LocalGestionDto dto = servicio.obtener(50L, ADMIN);
        assertThat(dto.ocupacionesVigentes()).isEqualTo(1L);
        assertThat(dto.ocupacionesFuturas()).isEqualTo(1L);
        verify(ocupacionRepository).contarVigentesDelLocal(50L, HOY);
    }

    @Test
    void mantenimientosNuevosRechazadosEnLocalInactivoYPermitidosEnZonaComunD10() {
        MantenimientoService mantenimientoService = new MantenimientoService(mantenimientoRepository, obligacionRepository,
            autorizacion, mock(IntervencionRepository.class), centroRepository, localRepository);
        when(mantenimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CrearMantenimientoRequest peticion = new CrearMantenimientoRequest(HOY.plusDays(10), "Revision sintetica");
        ObligacionMantenimiento delLocal = mock(ObligacionMantenimiento.class);
        when(delLocal.getCentro()).thenReturn(centro);
        when(delLocal.getLocal()).thenReturn(local);
        when(obligacionRepository.findById(40L)).thenReturn(Optional.of(delLocal));
        when(localRepository.bloquearYEstaActivo(50L)).thenReturn(false);
        assertThatThrownBy(() -> mantenimientoService.crear(40L, peticion, ADMIN))
            .isInstanceOf(ConflictoException.class).hasMessageContaining("local");
        InOrder orden = inOrder(centroRepository, localRepository);
        orden.verify(centroRepository).findParaAlta(5L);
        orden.verify(localRepository).bloquearYEstaActivo(50L);
        verify(mantenimientoRepository, never()).save(any());
        // Zona comun (sin local) con el centro activo: se admite y no se consulta ningun local.
        ObligacionMantenimiento zonaComun = mock(ObligacionMantenimiento.class, RETURNS_DEEP_STUBS);
        when(zonaComun.getCentro()).thenReturn(centro);
        when(zonaComun.getLocal()).thenReturn(null);
        when(obligacionRepository.findById(41L)).thenReturn(Optional.of(zonaComun));
        try {
            mantenimientoService.crear(41L, peticion, ADMIN);
        } catch (RuntimeException ignorada) {
            // El DTO de salida usa datos del mock que aqui no importan; lo relevante es que se guardo.
        }
        verify(mantenimientoRepository).save(any());
        verify(localRepository, times(1)).bloquearYEstaActivo(any());
    }

    private void assertConflicto(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOf(ConflictoCodificadoException.class)
            .satisfies(e -> assertThat(((ConflictoCodificadoException) e).getCodigo()).isEqualTo(codigo));
    }

    private String huella() { return LocalGestionService.huella(local); }

    private EditarLocalRequest edicion(String nombre) {
        return new EditarLocalRequest(null, null, nombre, local.getPlanta(), local.getUbicacion(), huella());
    }

    private static CrearLocalRequest peticion(String codigo, String nombre) {
        return new CrearLocalRequest(codigo, nombre, null, null);
    }

    private static Centro centro(Long id, boolean activo) {
        Centro c = new Centro("CC-" + id, "Centro " + id, null, "Sevilla", null, LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(c, "id", id);
        c.cambiarActivo(activo);
        return c;
    }

    private static LocalComercial local(Long id, Centro centro, String codigo, String nombre, String planta, String ubicacion) {
        LocalComercial l = new LocalComercial(centro, codigo, nombre, planta, ubicacion, LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(l, "id", id);
        return l;
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
