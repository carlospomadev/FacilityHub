package com.facilityhub.fh013;

import com.facilityhub.api.dto.CrearMantenimientoRequest;
import com.facilityhub.api.dto.centro.CambiarActivoCentroRequest;
import com.facilityhub.api.dto.centro.CentroGestionDto;
import com.facilityhub.api.dto.centro.CrearCentroRequest;
import com.facilityhub.api.dto.centro.EditarCentroRequest;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FH-013 / RF-03: reglas de la gestion de centros (Mockito, sin BD). Datos sinteticos.
class CentrosFH013UnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 25);
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
    private BloqueoCatalogo bloqueo;
    private DocumentoAutorizacionService autorizacion;
    private CentroGestionService servicio;
    private Centro centro;

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
        bloqueo = mock(BloqueoCatalogo.class);
        autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        servicio = new CentroGestionService(centroRepository, localRepository, instalacionRepository, obligacionRepository,
            ocupacionRepository, mantenimientoRepository, autorizacion, auditoria, bloqueo, RELOJ);
        usuario(ADMIN, "ADMIN"); usuario(EXPLOTACION, "EXPLOTACION"); usuario(INQUILINO, "INQUILINO"); usuario(PROVEEDOR, "PROVEEDOR");
        centro = centro(5L, "CC-NORTE", "Centro Norte", "Sevilla", true);
        when(centroRepository.findParaAlta(5L)).thenReturn(Optional.of(centro));
        when(centroRepository.findById(5L)).thenReturn(Optional.of(centro));
        when(centroRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(centro));
        when(centroRepository.findAll()).thenReturn(List.of(centro, centro(6L, "CC-SUR", "Plaza Mayor", "Málaga", false)));
        when(centroRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Centro c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 99L);
            return c;
        });
        when(localRepository.countByCentroIdAndActivoTrue(5L)).thenReturn(4L);
        when(instalacionRepository.countByCentroId(5L)).thenReturn(3L);
        when(obligacionRepository.countByCentroIdAndActivoTrue(5L)).thenReturn(2L);
    }

    @Test
    void altaNormalizaElCodigoBloqueaElCatalogoYAudita() {
        CentroGestionDto dto = servicio.crear(new CrearCentroRequest(" cc-este ", "  Centro Este ", " ", " Cádiz ", null), ADMIN);
        assertThat(dto.codigo()).isEqualTo("CC-ESTE");
        assertThat(dto.nombre()).isEqualTo("Centro Este");
        assertThat(dto.ciudad()).isEqualTo("Cádiz");
        assertThat(dto.direccion()).isNull();
        assertThat(dto.activo()).isTrue();
        assertThat(dto.huella()).hasSize(64);
        InOrder orden = inOrder(bloqueo, centroRepository);
        orden.verify(bloqueo).bloquearCentros();
        orden.verify(centroRepository).existsByCodigo("CC-ESTE");
        orden.verify(centroRepository).saveAndFlush(any());
        verify(auditoria).registrar(eq("CENTRO"), eq(99L), eq("CREAR"), any(), anyMap());
    }

    @Test
    void codigoNoValidoYDuplicadosD2D3() {
        assertThatThrownBy(() -> servicio.crear(peticion("A", "Nuevo", "Sevilla"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.crear(peticion("CON ESPACIO", "Nuevo", "Sevilla"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.crear(peticion("ÑANDU", "Nuevo", "Sevilla"), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        when(centroRepository.existsByCodigo("CC-NORTE")).thenReturn(true);
        assertConflicto(() -> servicio.crear(peticion("cc-norte", "Otro", "Sevilla"), ADMIN), "CODIGO_DUPLICADO");
        // Nombre + ciudad repetidos sin distinguir mayusculas ni espacios exteriores (tambien frente a un centro inactivo).
        assertConflicto(() -> servicio.crear(peticion("CC-N2", "  CENTRO NORTE ", " sevilla "), ADMIN), "NOMBRE_DUPLICADO");
        assertConflicto(() -> servicio.crear(peticion("CC-S2", "plaza mayor", "MÁLAGA"), ADMIN), "NOMBRE_DUPLICADO");
        verify(centroRepository, never()).saveAndFlush(any());
        // El mismo nombre en otra ciudad si se admite.
        assertThat(servicio.crear(peticion("CC-N3", "Centro Norte", "Huelva"), ADMIN).ciudad()).isEqualTo("Huelva");
    }

    @Test
    void permisosD1() {
        assertThat(servicio.listar(EXPLOTACION)).hasSize(1);
        assertThat(servicio.obtener(5L, EXPLOTACION).codigo()).isEqualTo("CC-NORTE");
        for (String email : new String[] {EXPLOTACION, INQUILINO, PROVEEDOR}) {
            assertThatThrownBy(() -> servicio.crear(peticion("X-1", "X", "Y"), email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.editar(5L, edicion("Centro Norte", "Sevilla"), email))
                .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(false, huella(), true), email))
                .isInstanceOf(AccessDeniedException.class);
        }
        for (String email : new String[] {INQUILINO, PROVEEDOR}) {
            assertThatThrownBy(() -> servicio.listar(email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.obtener(5L, email)).isInstanceOf(AccessDeniedException.class);
        }
        verify(bloqueo, never()).bloquearCentros();
        verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
    }

    @Test
    void codigoInmutableYEdicionConcurrenteD7() {
        assertThatThrownBy(() -> servicio.editar(5L, new EditarCentroRequest("OTRO", "Centro Norte", null, "Sevilla", null,
            huella()), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        String huellaInicial = huella();
        // Primer usuario guarda con la huella vigente.
        CentroGestionDto dto = servicio.editar(5L, new EditarCentroRequest("cc-norte", "Centro Norte II", "Av. Sintetica 1",
            "Sevilla", null, huellaInicial), ADMIN);
        assertThat(dto.nombre()).isEqualTo("Centro Norte II");
        verify(auditoria).registrar(eq("CENTRO"), eq(5L), eq("EDITAR"), any(), anyMap());
        // Segundo usuario con la huella antigua: 409 sin sobrescribir.
        assertConflicto(() -> servicio.editar(5L, new EditarCentroRequest(null, "Tercer nombre", null, "Sevilla", null,
            huellaInicial), ADMIN), "DATOS_DESACTUALIZADOS");
        assertThat(centro.getNombre()).isEqualTo("Centro Norte II");
        assertThat(centro.getDireccion()).isEqualTo("Av. Sintetica 1");
        // Renombrar a un nombre + ciudad ya usado por otro centro.
        assertConflicto(() -> servicio.editar(5L, new EditarCentroRequest(null, "Plaza Mayor", null, "Málaga", null,
            huella()), ADMIN), "NOMBRE_DUPLICADO");
    }

    @Test
    void desactivacionBloqueadaPorOcupacionVigenteD4() {
        when(ocupacionRepository.contarVigentesDelCentro(5L, HOY)).thenReturn(1L);
        assertConflicto(() -> servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(false, huella(), true), ADMIN),
            "CENTRO_EN_USO");
        assertThat(centro.isActivo()).isTrue();
    }

    @Test
    void desactivacionBloqueadaPorOcupacionFuturaAunqueSeConfirmeD4() {
        when(ocupacionRepository.contarFuturasDelCentro(5L, HOY)).thenReturn(1L);
        assertThatThrownBy(() -> servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(false, huella(), true), ADMIN))
            .isInstanceOf(ConflictoCodificadoException.class)
            .satisfies(e -> {
                ConflictoCodificadoException c = (ConflictoCodificadoException) e;
                assertThat(c.getCodigo()).isEqualTo("CENTRO_EN_USO");
                assertThat(c.getDatos()).containsEntry("ocupacionesFuturas", 1L).containsEntry("ocupacionesVigentes", 0L);
                assertThat(c.getMessage()).contains("1 ocupaciones futuras");
            });
        assertThat(centro.isActivo()).isTrue();
        assertThat(servicio.obtener(5L, EXPLOTACION).ocupacionesFuturas()).isEqualTo(1L);
        verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
    }

    @Test
    void auditoriaRegistraLasOcupacionesFuturas() {
        // Desactivar sin uso: el detalle lleva el contador (0). Si despues aparece una ocupacion futura (p. ej. por SQL),
        // la reactivacion la registra.
        servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(false, huella(), true), ADMIN);
        when(ocupacionRepository.contarFuturasDelCentro(5L, HOY)).thenReturn(2L);
        servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(true, huella(), null), ADMIN);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> detalle = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditoria).registrar(eq("CENTRO"), eq(5L), eq("DESACTIVAR"), any(), detalle.capture());
        assertThat(detalle.getValue()).containsEntry("ocupacionesFuturas", 0L);
        verify(auditoria).registrar(eq("CENTRO"), eq(5L), eq("REACTIVAR"), any(), detalle.capture());
        assertThat(detalle.getValue()).containsEntry("ocupacionesFuturas", 2L);
    }

    @Test
    void laFechaDeHoyEsLaDeMadrid() {
        // 24/11 23:30 UTC = 25/11 00:30 en Madrid: los recuentos usan el 25/11.
        Clock medianoche = Clock.fixed(Instant.parse("2026-11-24T23:30:00Z"), ZoneId.of("Europe/Madrid"));
        DocumentoAutorizacionService autMadrid = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, medianoche);
        CentroGestionService s = new CentroGestionService(centroRepository, localRepository, instalacionRepository,
            obligacionRepository, ocupacionRepository, mantenimientoRepository, autMadrid, auditoria, bloqueo, medianoche);
        s.obtener(5L, ADMIN);
        verify(ocupacionRepository).contarVigentesDelCentro(5L, LocalDate.of(2026, 11, 25));
        verify(ocupacionRepository).contarFuturasDelCentro(5L, LocalDate.of(2026, 11, 25));
    }

    @Test
    void desactivacionBloqueadaPorMantenimientoAbiertoD4() {
        when(mantenimientoRepository.contarAbiertosDelCentro(5L)).thenReturn(2L);
        assertConflicto(() -> servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(false, huella(), true), ADMIN),
            "CENTRO_EN_USO");
        assertThat(centro.isActivo()).isTrue();
    }

    @Test
    void desactivacionSinUsoExigeConfirmacionConservaDatosYReactiva() {
        assertConflicto(() -> servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(false, huella(), null), ADMIN),
            "IMPACTO_NO_CONFIRMADO");
        assertThat(centro.isActivo()).isTrue();
        // Orden de bloqueo: catalogo y despues fila, antes de contar.
        InOrder orden = inOrder(bloqueo, centroRepository, mantenimientoRepository);
        orden.verify(bloqueo).bloquearCentros();
        orden.verify(centroRepository).findParaAlta(5L);
        orden.verify(mantenimientoRepository).contarAbiertosDelCentro(5L);

        CentroGestionDto dto = servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(false, huella(), true), ADMIN);
        assertThat(dto.activo()).isFalse();
        // Sin cascada: locales, instalaciones y obligaciones siguen igual.
        assertThat(dto.localesActivos()).isEqualTo(4L);
        assertThat(dto.instalaciones()).isEqualTo(3L);
        assertThat(dto.obligacionesActivas()).isEqualTo(2L);
        verifyNoMoreInteractionsDeBorrado();
        verify(auditoria).registrar(eq("CENTRO"), eq(5L), eq("DESACTIVAR"), any(), anyMap());

        assertConflicto(() -> servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(true, "x", null), ADMIN), "DATOS_DESACTUALIZADOS");
        assertThat(servicio.cambiarActivo(5L, new CambiarActivoCentroRequest(true, huella(), null), ADMIN).activo()).isTrue();
        verify(auditoria).registrar(eq("CENTRO"), eq(5L), eq("REACTIVAR"), any(), anyMap());
    }

    @Test
    void noSeCreanMantenimientosEnCentroInactivoD6() {
        IntervencionRepository intervencionRepository = mock(IntervencionRepository.class);
        MantenimientoService mantenimientoService = new MantenimientoService(mantenimientoRepository, obligacionRepository,
            autorizacion, intervencionRepository, centroRepository);
        ObligacionMantenimiento obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getCentro()).thenReturn(centro);
        when(obligacionRepository.findById(40L)).thenReturn(Optional.of(obligacion));
        centro.cambiarActivo(false);
        CrearMantenimientoRequest peticion = new CrearMantenimientoRequest(HOY.plusDays(10), "Revision sintetica");
        assertThatThrownBy(() -> mantenimientoService.crear(40L, peticion, ADMIN)).isInstanceOf(ConflictoException.class);
        verify(centroRepository).findParaAlta(5L);
        verify(mantenimientoRepository, never()).save(any());
    }

    private void verifyNoMoreInteractionsDeBorrado() {
        verify(centroRepository, never()).delete(any());
        verify(localRepository, never()).save(any());
        verify(obligacionRepository, never()).save(any());
        verify(instalacionRepository, never()).save(any());
    }

    private void assertConflicto(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOf(ConflictoCodificadoException.class)
            .satisfies(e -> assertThat(((ConflictoCodificadoException) e).getCodigo()).isEqualTo(codigo));
    }

    private String huella() { return CentroGestionService.huella(centro); }

    private EditarCentroRequest edicion(String nombre, String ciudad) {
        return new EditarCentroRequest(null, nombre, null, ciudad, null, huella());
    }

    private static CrearCentroRequest peticion(String codigo, String nombre, String ciudad) {
        return new CrearCentroRequest(codigo, nombre, null, ciudad, null);
    }

    private static Centro centro(Long id, String codigo, String nombre, String ciudad, boolean activo) {
        Centro c = new Centro(codigo, nombre, null, ciudad, null, java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(c, "id", id);
        c.cambiarActivo(activo);
        return c;
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
