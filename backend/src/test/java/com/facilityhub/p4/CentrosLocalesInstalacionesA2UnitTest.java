package com.facilityhub.p4;

import com.facilityhub.api.dto.CentroDto;
import com.facilityhub.api.dto.InstalacionDto;
import com.facilityhub.api.dto.LocalDto;
import com.facilityhub.domain.Centro;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.LocalComercial;
import com.facilityhub.domain.Rol;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.CentroRepository;
import com.facilityhub.repository.InstalacionRepository;
import com.facilityhub.repository.LocalComercialRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.UsuarioRepository;
import com.facilityhub.service.CentroService;
import com.facilityhub.service.DocumentoAutorizacionService;
import com.facilityhub.service.LocalDetalleService;
import com.facilityhub.service.RecursoNoDisponibleException;
import com.facilityhub.service.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FH-005A2-B - Reglas de centros, locales e instalaciones con dobles (sin Spring, BD ni Flyway).
 * Preparada en A2-B; NO se ejecuta con el comando restringido de las cuatro suites de FH-005A1.
 */
class CentrosLocalesInstalacionesA2UnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-16T23:30:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 17);
    private static final Long INQUILINO_ID = 801L;
    private static final Long CENTRO_PROPIO = 501L;
    private static final Long CENTRO_AJENO = 502L;
    private static final Long LOCAL_PROPIO = 901L;
    private static final Long LOCAL_AJENO = 902L;

    private UsuarioRepository usuarioRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private CentroRepository centroRepository;
    private LocalComercialRepository localRepository;
    private InstalacionRepository instalacionRepository;
    private DocumentoAutorizacionService autorizacion;
    private CentroService centroService;
    private LocalDetalleService localService;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        centroRepository = mock(CentroRepository.class);
        localRepository = mock(LocalComercialRepository.class);
        instalacionRepository = mock(InstalacionRepository.class);
        autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        centroService = new CentroService(centroRepository, localRepository, autorizacion);
        localService = new LocalDetalleService(localRepository, ocupacionRepository, instalacionRepository, autorizacion);

        when(centroRepository.esVisibleParaInquilino(CENTRO_PROPIO, INQUILINO_ID, HOY)).thenReturn(true);
        when(centroRepository.esVisibleParaInquilino(CENTRO_AJENO, INQUILINO_ID, HOY)).thenReturn(false);
        when(localRepository.existsById(LOCAL_PROPIO)).thenReturn(true);
        when(localRepository.existsById(LOCAL_AJENO)).thenReturn(true);
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY)).thenReturn(true);
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_AJENO, INQUILINO_ID, HOY)).thenReturn(false);
    }

    // ---- inquilinoConAcceso ----

    @Test
    void inquilinoConAccesoSoloParaRolInquilinoConInquilinoActivo() {
        assertThat(autorizacion.inquilinoConAcceso(usuario("INQUILINO", inquilino(true)))).contains(INQUILINO_ID);
        assertThat(autorizacion.inquilinoConAcceso(usuario("INQUILINO", inquilino(false)))).isEmpty();
        assertThat(autorizacion.inquilinoConAcceso(usuario("INQUILINO", null))).isEmpty();
        assertThat(autorizacion.inquilinoConAcceso(usuario("ADMIN", null))).isEmpty();
        assertThat(autorizacion.inquilinoConAcceso(usuario("DESCONOCIDO", inquilino(true)))).isEmpty();
        assertThat(autorizacion.inquilinoConAcceso(usuario(null, inquilino(true)))).isEmpty();
    }

    // ---- GET /api/centros ----

    @Test
    void gestionListaTodosLosCentrosActivosComoAntes() {
        registrar("gestion@sintetico.test", usuario("EXPLOTACION", null));
        List<Centro> activos = List.of(centro(CENTRO_PROPIO), centro(CENTRO_AJENO));
        when(centroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(activos);

        List<CentroDto> centros = centroService.listarCentros("gestion@sintetico.test");

        assertThat(centros).extracting(CentroDto::id).containsExactly(CENTRO_PROPIO, CENTRO_AJENO);
        verify(centroRepository, never()).findVisiblesParaInquilino(anyLong(), any());
    }

    @Test
    void inquilinoListaSoloSusCentrosFiltradosEnConsulta() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));
        List<Centro> visibles = List.of(centro(CENTRO_PROPIO));
        when(centroRepository.findVisiblesParaInquilino(INQUILINO_ID, HOY)).thenReturn(visibles);

        List<CentroDto> centros = centroService.listarCentros("inq@sintetico.test");

        assertThat(centros).extracting(CentroDto::id).containsExactly(CENTRO_PROPIO);
        verify(centroRepository, never()).findByActivoTrueOrderByNombreAsc();
    }

    @Test
    void inquilinoSinAsociacionActivaORolDesconocidoRecibeListaVacia() {
        registrar("inactivo@sintetico.test", usuario("INQUILINO", inquilino(false)));
        registrar("raro@sintetico.test", usuario("DESCONOCIDO", null));

        assertThat(centroService.listarCentros("inactivo@sintetico.test")).isEmpty();
        assertThat(centroService.listarCentros("raro@sintetico.test")).isEmpty();
        verifyNoInteractions(centroRepository);
    }

    @Test
    void usuarioDesactivadoOInexistenteEsRechazadoConElCriterioVigente() {
        when(usuarioRepository.findByEmailAndActivoTrue("baja@sintetico.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> centroService.listarCentros("baja@sintetico.test")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> centroService.obtenerCentro(CENTRO_PROPIO, "baja@sintetico.test")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> centroService.listarLocales(CENTRO_PROPIO, "baja@sintetico.test")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> localService.listarInstalaciones(LOCAL_PROPIO, "baja@sintetico.test")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> centroService.listarCentros(null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(centroRepository, instalacionRepository);
    }

    // ---- GET /api/centros/{id} ----

    @Test
    void inquilinoVeElDetalleDeSuCentro() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));
        Centro propio = centro(CENTRO_PROPIO);
        when(centroRepository.findById(CENTRO_PROPIO)).thenReturn(Optional.of(propio));

        assertThat(centroService.obtenerCentro(CENTRO_PROPIO, "inq@sintetico.test").id()).isEqualTo(CENTRO_PROPIO);
    }

    @Test
    void centroAjenoEInexistenteDanElMismo404SinLeerElCentro() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));

        assertThatThrownBy(() -> centroService.obtenerCentro(CENTRO_AJENO, "inq@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class).hasMessage("Recurso no encontrado");
        assertThatThrownBy(() -> centroService.obtenerCentro(Long.MAX_VALUE, "inq@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class).hasMessage("Recurso no encontrado");
        verify(centroRepository, never()).findById(anyLong());
    }

    @Test
    void rolDegradadoEnBdSinInquilinoNoVeDetalles() {
        // El JWT pudo ser de EXPLOTACION; en BD ya es INQUILINO sin inquilino asociado.
        registrar("cambio@sintetico.test", usuario("INQUILINO", null));

        assertThatThrownBy(() -> centroService.obtenerCentro(CENTRO_PROPIO, "cambio@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class);
        assertThatThrownBy(() -> centroService.listarLocales(CENTRO_PROPIO, "cambio@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class);
    }

    @Test
    void gestionConservaElDetalleYElMensajePrevio() {
        registrar("admin@sintetico.test", usuario("ADMIN", null));
        Centro ajeno = centro(CENTRO_AJENO);
        when(centroRepository.findById(CENTRO_AJENO)).thenReturn(Optional.of(ajeno));

        assertThat(centroService.obtenerCentro(CENTRO_AJENO, "admin@sintetico.test").id()).isEqualTo(CENTRO_AJENO);
        assertThatThrownBy(() -> centroService.obtenerCentro(Long.MAX_VALUE, "admin@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);
        verify(centroRepository, never()).esVisibleParaInquilino(anyLong(), anyLong(), any());
    }

    // ---- GET /api/centros/{id}/locales ----

    @Test
    void inquilinoSoloRecibeSusLocalesDelCentroCompartido() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));
        List<LocalComercial> propios = List.of(local(LOCAL_PROPIO));
        when(localRepository.findVisiblesEnCentroParaInquilino(CENTRO_PROPIO, INQUILINO_ID, HOY)).thenReturn(propios);

        List<LocalDto> locales = centroService.listarLocales(CENTRO_PROPIO, "inq@sintetico.test");

        assertThat(locales).extracting(LocalDto::id).containsExactly(LOCAL_PROPIO);
        verify(localRepository, never()).findByCentroIdAndActivoTrueOrderByCodigoAsc(anyLong());
    }

    @Test
    void localesDeCentroAjenoDan404SinConsultarLocales() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));

        assertThatThrownBy(() -> centroService.listarLocales(CENTRO_AJENO, "inq@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class);
        verify(localRepository, never()).findVisiblesEnCentroParaInquilino(anyLong(), anyLong(), any());
        verify(localRepository, never()).findByCentroIdAndActivoTrueOrderByCodigoAsc(anyLong());
    }

    @Test
    void gestionListaTodosLosLocalesActivosComoAntes() {
        registrar("admin@sintetico.test", usuario("ADMIN", null));
        when(centroRepository.existsById(CENTRO_PROPIO)).thenReturn(true);
        List<LocalComercial> activos = List.of(local(LOCAL_PROPIO), local(LOCAL_AJENO));
        when(localRepository.findByCentroIdAndActivoTrueOrderByCodigoAsc(CENTRO_PROPIO)).thenReturn(activos);

        assertThat(centroService.listarLocales(CENTRO_PROPIO, "admin@sintetico.test"))
            .extracting(LocalDto::id).containsExactly(LOCAL_PROPIO, LOCAL_AJENO);
        assertThatThrownBy(() -> centroService.listarLocales(Long.MAX_VALUE, "admin@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---- GET /api/locales/{id}/instalaciones ----

    @Test
    void inquilinoVeLasInstalacionesDeSuLocal() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));
        when(instalacionRepository.findByLocalIdOrderByNombreAsc(LOCAL_PROPIO)).thenReturn(List.of());

        List<InstalacionDto> instalaciones = localService.listarInstalaciones(LOCAL_PROPIO, "inq@sintetico.test");

        assertThat(instalaciones).isEmpty();
        verify(instalacionRepository).findByLocalIdOrderByNombreAsc(LOCAL_PROPIO);
    }

    @Test
    void instalacionesDeLocalAjenoOInexistenteDanElMismo404() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));

        assertThatThrownBy(() -> localService.listarInstalaciones(LOCAL_AJENO, "inq@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class).hasMessage("Recurso no encontrado");
        assertThatThrownBy(() -> localService.listarInstalaciones(Long.MAX_VALUE, "inq@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class).hasMessage("Recurso no encontrado");
        verifyNoInteractions(instalacionRepository);
    }

    @Test
    void instalacionesConInquilinoInactivoDan404() {
        registrar("inactivo@sintetico.test", usuario("INQUILINO", inquilino(false)));

        assertThatThrownBy(() -> localService.listarInstalaciones(LOCAL_PROPIO, "inactivo@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class);
        verifyNoInteractions(instalacionRepository);
    }

    @Test
    void gestionVeInstalacionesDeCualquierLocalYConservaElMensajePrevio() {
        registrar("explotacion@sintetico.test", usuario("EXPLOTACION", null));
        when(instalacionRepository.findByLocalIdOrderByNombreAsc(LOCAL_AJENO)).thenReturn(List.of());

        assertThat(localService.listarInstalaciones(LOCAL_AJENO, "explotacion@sintetico.test")).isEmpty();
        assertThatThrownBy(() -> localService.listarInstalaciones(Long.MAX_VALUE, "explotacion@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);
        verify(ocupacionRepository, never()).existeOcupacionVigente(anyLong(), anyLong(), any());
    }

    // ---- FH-005A2-B1: la consulta de ocupacion vigente es la unica fuente de verdad para el INQUILINO ----
    // (local/centro inactivos hacen que existeOcupacionVigente devuelva false; aqui se simula ese resultado).

    @Test
    void sinOcupacionVigenteEnLocalActivoInquilinoActualEInstalacionesDan404SinLeerDatos() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY)).thenReturn(false);

        assertThatThrownBy(() -> localService.obtenerInquilinoActual(LOCAL_PROPIO, "inq@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class).hasMessage("Recurso no encontrado");
        assertThatThrownBy(() -> localService.listarInstalaciones(LOCAL_PROPIO, "inq@sintetico.test"))
            .isInstanceOf(RecursoNoDisponibleException.class).hasMessage("Recurso no encontrado");
        verify(ocupacionRepository, never()).findByLocalIdAndActivoTrue(anyLong());
        verifyNoInteractions(instalacionRepository);
    }

    @Test
    void gestionNoConsultaLaOcupacionParaInstalacionesNiOcupante() {
        registrar("admin@sintetico.test", usuario("ADMIN", null));
        when(instalacionRepository.findByLocalIdOrderByNombreAsc(LOCAL_PROPIO)).thenReturn(List.of());

        assertThat(localService.listarInstalaciones(LOCAL_PROPIO, "admin@sintetico.test")).isEmpty();
        verify(ocupacionRepository, never()).existeOcupacionVigente(anyLong(), anyLong(), any());
    }

    // ---- Dobles sinteticos ----

    private void registrar(String email, Usuario usuario) {
        when(usuarioRepository.findByEmailAndActivoTrue(email)).thenReturn(Optional.of(usuario));
    }

    private static Usuario usuario(String codigoRol, Inquilino inquilino) {
        Usuario usuario = mock(Usuario.class);
        Rol rol = mock(Rol.class);
        when(rol.getCodigo()).thenReturn(codigoRol);
        when(usuario.getRol()).thenReturn(rol);
        when(usuario.getInquilino()).thenReturn(inquilino);
        when(usuario.isActivo()).thenReturn(true);
        return usuario;
    }

    private static Inquilino inquilino(boolean activo) {
        Inquilino inquilino = mock(Inquilino.class);
        when(inquilino.getId()).thenReturn(INQUILINO_ID);
        when(inquilino.isActivo()).thenReturn(activo);
        return inquilino;
    }

    private static Centro centro(Long id) {
        Centro centro = mock(Centro.class);
        when(centro.getId()).thenReturn(id);
        when(centro.getCodigo()).thenReturn("SINT-" + id);
        when(centro.isActivo()).thenReturn(true);
        return centro;
    }

    private static LocalComercial local(Long id) {
        LocalComercial local = mock(LocalComercial.class);
        when(local.getId()).thenReturn(id);
        when(local.getCodigo()).thenReturn("SINT-L" + id);
        when(local.isActivo()).thenReturn(true);
        return local;
    }
}
