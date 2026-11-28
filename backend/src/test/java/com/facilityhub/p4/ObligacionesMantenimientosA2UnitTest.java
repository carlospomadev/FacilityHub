package com.facilityhub.p4;

import com.facilityhub.api.dto.CambiarEstadoMantenimientoRequest;
import com.facilityhub.api.dto.CrearMantenimientoRequest;
import com.facilityhub.api.dto.MantenimientoDto;
import com.facilityhub.api.dto.MantenimientoInquilinoDto;
import com.facilityhub.api.dto.ObligacionDto;
import com.facilityhub.domain.Centro;
import com.facilityhub.domain.EstadoMantenimiento;
import com.facilityhub.domain.EstadoObligacion;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.LocalComercial;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.ObligacionMantenimiento;
import com.facilityhub.domain.Rol;
import com.facilityhub.domain.TipoMantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.CentroRepository;
import com.facilityhub.repository.IntervencionRepository;
import com.facilityhub.repository.LocalComercialRepository;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.ObligacionMantenimientoRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.UsuarioRepository;
import com.facilityhub.service.ConflictoException;
import com.facilityhub.service.DocumentoAutorizacionService;
import com.facilityhub.service.MantenimientoService;
import com.facilityhub.service.ObligacionService;
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
 * FH-005A2-C - Obligaciones y mantenimientos con dobles (JUnit + Mockito; sin contexto, BD ni migraciones).
 * El servicio de autorizacion es el real; sus repositorios son dobles.
 */
class ObligacionesMantenimientosA2UnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-16T23:30:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 17);
    private static final Long INQUILINO_ID = 801L;
    private static final Long LOCAL_PROPIO = 901L;
    private static final Long LOCAL_AJENO = 902L;
    private static final Long OBLIGACION_PROPIA = 3001L;
    private static final Long OBLIGACION_AJENA = 3002L;
    private static final Long OBLIGACION_INACTIVA = 3003L;
    private static final Long MANTENIMIENTO_ID = 7001L;

    private UsuarioRepository usuarioRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private LocalComercialRepository localRepository;
    private ObligacionMantenimientoRepository obligacionRepository;
    private MantenimientoRepository mantenimientoRepository;
    private ObligacionService obligacionService;
    private MantenimientoService mantenimientoService;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        localRepository = mock(LocalComercialRepository.class);
        obligacionRepository = mock(ObligacionMantenimientoRepository.class);
        mantenimientoRepository = mock(MantenimientoRepository.class);
        DocumentoAutorizacionService autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        obligacionService = new ObligacionService(localRepository, obligacionRepository, ocupacionRepository, autorizacion);
        // FH-013 (D6): el alta de mantenimientos bloquea la fila del centro y exige que este activo.
        CentroRepository centroRepository = mock(CentroRepository.class);
        Centro centroActivo = mock(Centro.class);
        when(centroActivo.isActivo()).thenReturn(true);
        when(centroRepository.findParaAlta(any())).thenReturn(Optional.of(centroActivo));
        mantenimientoService = new MantenimientoService(mantenimientoRepository, obligacionRepository, autorizacion,
            mock(IntervencionRepository.class), centroRepository, localRepository);
        // FH-014 (D10): los locales de estas pruebas estan activos.
        when(localRepository.bloquearYEstaActivo(any())).thenReturn(true);

        // existeOcupacionVigente ya incluye ocupacion activa y vigente, local activo y centro activo (B1).
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY)).thenReturn(true);
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_AJENO, INQUILINO_ID, HOY)).thenReturn(false);
        // P6-C2: ocupacion vigente concreta, iniciada antes de los mantenimientos sinteticos (historial protegido).
        com.facilityhub.domain.OcupacionLocal ocupacion = mock(com.facilityhub.domain.OcupacionLocal.class);
        when(ocupacion.getId()).thenReturn(6001L);
        when(ocupacion.getFechaInicio()).thenReturn(LocalDate.of(2000, 1, 1));
        when(ocupacionRepository.ocupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY)).thenReturn(Optional.of(ocupacion));
        when(localRepository.existsById(LOCAL_PROPIO)).thenReturn(true);
        when(localRepository.existsById(LOCAL_AJENO)).thenReturn(true);

        ObligacionMantenimiento propia = obligacion(OBLIGACION_PROPIA, LOCAL_PROPIO, true);
        ObligacionMantenimiento ajena = obligacion(OBLIGACION_AJENA, LOCAL_AJENO, true);
        ObligacionMantenimiento inactiva = obligacion(OBLIGACION_INACTIVA, LOCAL_PROPIO, false);
        when(obligacionRepository.findById(OBLIGACION_PROPIA)).thenReturn(Optional.of(propia));
        when(obligacionRepository.findById(OBLIGACION_AJENA)).thenReturn(Optional.of(ajena));
        when(obligacionRepository.findById(OBLIGACION_INACTIVA)).thenReturn(Optional.of(inactiva));
        when(obligacionRepository.existsById(OBLIGACION_PROPIA)).thenReturn(true);
        when(obligacionRepository.existsById(OBLIGACION_AJENA)).thenReturn(true);
    }

    // ---- GET /api/locales/{localId}/obligaciones ----

    @Test
    void inquilinoListaLasObligacionesDeSuLocal() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));
        List<ObligacionMantenimiento> lista = List.of(obligacionCompleta(OBLIGACION_PROPIA, LOCAL_PROPIO));
        when(obligacionRepository.findByLocalIdAndActivoTrueOrderByProximaFechaAsc(LOCAL_PROPIO)).thenReturn(lista);

        List<ObligacionDto> obligaciones = obligacionService.listarPorLocal(LOCAL_PROPIO, "inq@sintetico.test");

        assertThat(obligaciones).extracting(ObligacionDto::id).containsExactly(OBLIGACION_PROPIA);
    }

    @Test
    void obligacionesDeLocalAjenoInexistenteOSinAccesoVigenteDanElMismo404SinConsultar() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));

        for (Long local : new Long[] {LOCAL_AJENO, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> obligacionService.listarPorLocal(local, "inq@sintetico.test"))
                .isInstanceOf(RecursoNoDisponibleException.class).hasMessage("Recurso no encontrado");
        }
        verify(obligacionRepository, never()).findByLocalIdAndActivoTrueOrderByProximaFechaAsc(anyLong());
    }

    @Test
    void inquilinoInactivoSinAsociacionORolDesconocidoNoVeObligaciones() {
        registrar("inactivo@sintetico.test", usuario("INQUILINO", inquilino(false)));
        registrar("sin@sintetico.test", usuario("INQUILINO", null));
        registrar("raro@sintetico.test", usuario("DESCONOCIDO", inquilino(true)));

        for (String email : new String[] {"inactivo@sintetico.test", "sin@sintetico.test", "raro@sintetico.test"}) {
            assertThatThrownBy(() -> obligacionService.listarPorLocal(LOCAL_PROPIO, email))
                .isInstanceOf(RecursoNoDisponibleException.class);
        }
        verify(obligacionRepository, never()).findByLocalIdAndActivoTrueOrderByProximaFechaAsc(anyLong());
    }

    @Test
    void gestionConservaElListadoYElMensajePrevioDeObligaciones() {
        registrar("explotacion@sintetico.test", usuario("EXPLOTACION", null));
        List<ObligacionMantenimiento> lista = List.of(obligacionCompleta(OBLIGACION_AJENA, LOCAL_AJENO));
        when(obligacionRepository.findByLocalIdAndActivoTrueOrderByProximaFechaAsc(LOCAL_AJENO)).thenReturn(lista);

        assertThat(obligacionService.listarPorLocal(LOCAL_AJENO, "explotacion@sintetico.test")).hasSize(1);
        assertThatThrownBy(() -> obligacionService.listarPorLocal(Long.MAX_VALUE, "explotacion@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);
        verify(ocupacionRepository, never()).existeOcupacionVigente(anyLong(), anyLong(), any());
    }

    // ---- GET /api/obligaciones/{id}/mantenimientos ----

    @Test
    void inquilinoRecibeMantenimientosDeSuObligacionSinObservaciones() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));
        List<Mantenimiento> lista = List.of(mantenimiento(OBLIGACION_PROPIA, "Observacion interna sintetica"));
        when(mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(OBLIGACION_PROPIA)).thenReturn(lista);

        List<?> resultado = mantenimientoService.listarPorObligacion(OBLIGACION_PROPIA, "inq@sintetico.test");

        assertThat(resultado).hasSize(1).allSatisfy(dto -> assertThat(dto).isInstanceOf(MantenimientoInquilinoDto.class));
        assertThat(MantenimientoInquilinoDto.class.getRecordComponents())
            .extracting(c -> c.getName()).doesNotContain("observaciones");
        assertThat(resultado.get(0).toString()).doesNotContain("Observacion interna sintetica");
    }

    @Test
    void obligacionAjenaInexistenteOInactivaDanElMismo404SinLeerMantenimientos() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));

        for (Long obligacion : new Long[] {OBLIGACION_AJENA, Long.MAX_VALUE, OBLIGACION_INACTIVA}) {
            assertThatThrownBy(() -> mantenimientoService.listarPorObligacion(obligacion, "inq@sintetico.test"))
                .isInstanceOf(RecursoNoDisponibleException.class).hasMessage("Recurso no encontrado");
        }
        verify(mantenimientoRepository, never()).findByObligacionIdOrderByFechaProgramadaDesc(anyLong());
    }

    @Test
    void gestionConservaElDtoCompletoConObservaciones() {
        registrar("admin@sintetico.test", usuario("ADMIN", null));
        List<Mantenimiento> lista = List.of(mantenimiento(OBLIGACION_AJENA, "Observacion interna sintetica"));
        when(mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(OBLIGACION_AJENA)).thenReturn(lista);

        List<?> resultado = mantenimientoService.listarPorObligacion(OBLIGACION_AJENA, "admin@sintetico.test");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0)).isInstanceOf(MantenimientoDto.class);
        assertThat(((MantenimientoDto) resultado.get(0)).observaciones()).isEqualTo("Observacion interna sintetica");
        assertThatThrownBy(() -> mantenimientoService.listarPorObligacion(Long.MAX_VALUE, "admin@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---- Escrituras: POST y PATCH ----

    @Test
    void inquilinoORolDegradadoNoPuedenCrearNiCambiarEstadoYNoSeEscribe() {
        registrar("inq@sintetico.test", usuario("INQUILINO", inquilino(true)));
        // Token antiguo de EXPLOTACION, pero en BD ya es INQUILINO sin inquilino asociado.
        registrar("degradado@sintetico.test", usuario("INQUILINO", null));
        CrearMantenimientoRequest crear = new CrearMantenimientoRequest(HOY.plusDays(10), "Sintetico");
        CambiarEstadoMantenimientoRequest cambiar = new CambiarEstadoMantenimientoRequest(EstadoMantenimiento.EN_PROCESO, null, null);

        for (String email : new String[] {"inq@sintetico.test", "degradado@sintetico.test"}) {
            assertThatThrownBy(() -> mantenimientoService.crear(OBLIGACION_PROPIA, crear, email))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Operacion no permitida");
            assertThatThrownBy(() -> mantenimientoService.cambiarEstado(MANTENIMIENTO_ID, cambiar, email))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Operacion no permitida");
        }
        verify(mantenimientoRepository, never()).save(any());
        verify(mantenimientoRepository, never()).findById(anyLong());
    }

    @Test
    void usuarioDesactivadoOInexistenteRecibe403EnLecturasYEscrituras() {
        when(usuarioRepository.findByEmailAndActivoTrue("baja@sintetico.test")).thenReturn(Optional.empty());
        CrearMantenimientoRequest crear = new CrearMantenimientoRequest(HOY, null);
        CambiarEstadoMantenimientoRequest cambiar = new CambiarEstadoMantenimientoRequest(EstadoMantenimiento.EN_PROCESO, null, null);

        assertThatThrownBy(() -> obligacionService.listarPorLocal(LOCAL_PROPIO, "baja@sintetico.test")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> mantenimientoService.listarPorObligacion(OBLIGACION_PROPIA, "baja@sintetico.test")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> mantenimientoService.crear(OBLIGACION_PROPIA, crear, "baja@sintetico.test")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> mantenimientoService.cambiarEstado(MANTENIMIENTO_ID, cambiar, null)).isInstanceOf(AccessDeniedException.class);
        verify(mantenimientoRepository, never()).save(any());
        verifyNoInteractions(localRepository);
    }

    @Test
    void gestionCreaMantenimientoComoAntes() {
        registrar("explotacion@sintetico.test", usuario("EXPLOTACION", null));
        when(mantenimientoRepository.save(any(Mantenimiento.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        MantenimientoDto creado = mantenimientoService.crear(OBLIGACION_PROPIA,
            new CrearMantenimientoRequest(HOY.plusDays(10), "Sintetico"), "explotacion@sintetico.test");

        assertThat(creado.obligacionId()).isEqualTo(OBLIGACION_PROPIA);
        assertThat(creado.estado()).isEqualTo("PROGRAMADO");
        assertThat(creado.fechaProgramada()).isEqualTo(HOY.plusDays(10));
        assertThatThrownBy(() -> mantenimientoService.crear(Long.MAX_VALUE,
                new CrearMantenimientoRequest(HOY, null), "explotacion@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    // FH-006 / P6-C1 (cambio intencionado): este endpoint P5 ya no finaliza ni recalcula; FINALIZADO solo llega
    // presentando una intervencion y la proxima fecha solo cambia al aprobarla (no se elude la validacion).
    void finalizarPorElEndpointP5SeRechazaYNoRecalcula() {
        registrar("admin@sintetico.test", usuario("ADMIN", null));
        TipoMantenimiento tipo = mock(TipoMantenimiento.class);
        when(tipo.getPeriodicidadMeses()).thenReturn(6);
        ObligacionMantenimiento obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getId()).thenReturn(OBLIGACION_PROPIA);
        when(obligacion.getTipoMantenimiento()).thenReturn(tipo);
        Mantenimiento mantenimiento = new Mantenimiento(obligacion, HOY, "Sintetico");
        when(mantenimientoRepository.findById(MANTENIMIENTO_ID)).thenReturn(Optional.of(mantenimiento));
        LocalDate realizacion = LocalDate.of(2026, 11, 10);

        assertThatThrownBy(() -> mantenimientoService.cambiarEstado(MANTENIMIENTO_ID,
                new CambiarEstadoMantenimientoRequest(EstadoMantenimiento.FINALIZADO, realizacion, "Cierre sintetico"), "admin@sintetico.test"))
            .isInstanceOf(ConflictoException.class);

        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.PROGRAMADO);
        assertThat(mantenimiento.getFechaRealizacion()).isNull();
        verify(obligacion, never()).actualizarProximaFecha(any());
    }

    @Test
    void finalizarSinFechaTambienSeRechazaYNoRecalcula() {
        registrar("admin@sintetico.test", usuario("ADMIN", null));
        ObligacionMantenimiento obligacion = mock(ObligacionMantenimiento.class);
        Mantenimiento mantenimiento = new Mantenimiento(obligacion, HOY, "Sintetico");
        when(mantenimientoRepository.findById(MANTENIMIENTO_ID)).thenReturn(Optional.of(mantenimiento));

        assertThatThrownBy(() -> mantenimientoService.cambiarEstado(MANTENIMIENTO_ID,
                new CambiarEstadoMantenimientoRequest(EstadoMantenimiento.FINALIZADO, null, null), "admin@sintetico.test"))
            .isInstanceOf(ConflictoException.class);
        verify(obligacion, never()).actualizarProximaFecha(any());
    }

    @Test
    void cambioNoFinalizadoNoRecalculaYMantenimientoInexistenteConservaSu404() {
        registrar("admin@sintetico.test", usuario("ADMIN", null));
        ObligacionMantenimiento obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getId()).thenReturn(OBLIGACION_PROPIA);
        Mantenimiento mantenimiento = new Mantenimiento(obligacion, HOY, "Sintetico");
        when(mantenimientoRepository.findById(MANTENIMIENTO_ID)).thenReturn(Optional.of(mantenimiento));

        MantenimientoDto dto = mantenimientoService.cambiarEstado(MANTENIMIENTO_ID,
            new CambiarEstadoMantenimientoRequest(EstadoMantenimiento.EN_PROCESO, null, null), "admin@sintetico.test");

        assertThat(dto.estado()).isEqualTo("EN_PROCESO");
        verify(obligacion, never()).actualizarProximaFecha(any());
        assertThatThrownBy(() -> mantenimientoService.cambiarEstado(Long.MAX_VALUE,
                new CambiarEstadoMantenimientoRequest(EstadoMantenimiento.EN_PROCESO, null, null), "admin@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---- Dobles sinteticos (creados siempre antes de cualquier when que los use) ----

    private void registrar(String email, Usuario usuario) {
        when(usuarioRepository.findByEmailAndActivoTrue(email)).thenReturn(Optional.of(usuario));
    }

    private static Usuario usuario(String codigoRol, Inquilino inquilino) {
        Rol rol = mock(Rol.class);
        when(rol.getCodigo()).thenReturn(codigoRol);
        Usuario usuario = mock(Usuario.class);
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

    private static ObligacionMantenimiento obligacion(Long id, Long localId, boolean activa) {
        LocalComercial local = mock(LocalComercial.class);
        when(local.getId()).thenReturn(localId);
        ObligacionMantenimiento obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getId()).thenReturn(id);
        when(obligacion.getLocal()).thenReturn(local);
        when(obligacion.isActivo()).thenReturn(activa);
        Centro centro = mock(Centro.class);
        when(centro.getId()).thenReturn(1L);
        when(obligacion.getCentro()).thenReturn(centro);
        return obligacion;
    }

    private static ObligacionMantenimiento obligacionCompleta(Long id, Long localId) {
        TipoMantenimiento tipo = mock(TipoMantenimiento.class);
        when(tipo.getNombre()).thenReturn("Tipo sintetico");
        when(tipo.getPeriodicidadMeses()).thenReturn(12);
        ObligacionMantenimiento obligacion = obligacion(id, localId, true);
        when(obligacion.getTipoMantenimiento()).thenReturn(tipo);
        when(obligacion.getProximaFecha()).thenReturn(HOY.plusMonths(1));
        when(obligacion.getEstado()).thenReturn(EstadoObligacion.ACTIVA);
        return obligacion;
    }

    private static Mantenimiento mantenimiento(Long obligacionId, String observaciones) {
        ObligacionMantenimiento obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getId()).thenReturn(obligacionId);
        // P6-C2: el filtro historico por mantenimiento necesita el local de la obligacion.
        com.facilityhub.domain.LocalComercial local = mock(com.facilityhub.domain.LocalComercial.class);
        when(local.getId()).thenReturn(LOCAL_PROPIO);
        when(obligacion.getLocal()).thenReturn(local);
        Mantenimiento mantenimiento = new Mantenimiento(obligacion, HOY, "Descripcion sintetica");
        mantenimiento.cambiarEstado(EstadoMantenimiento.PENDIENTE, null, observaciones);
        return mantenimiento;
    }
}
