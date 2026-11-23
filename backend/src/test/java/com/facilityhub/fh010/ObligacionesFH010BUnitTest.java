package com.facilityhub.fh010;

import com.facilityhub.api.dto.CrearObligacionRequest;
import com.facilityhub.api.dto.ObligacionCentroDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// FH-010B / RF-09: reglas del alta de obligaciones (Mockito, sin BD). Datos sinteticos.
class ObligacionesFH010BUnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final String ADMIN = "admin@sintetico.test";
    private static final Long CENTRO = 10L, OTRO_CENTRO = 11L, LOCAL = 100L, INST_LOCAL = 500L, INST_ZC = 501L, TIPO = 7L;

    private UsuarioRepository usuarioRepository;
    private CentroRepository centroRepository;
    private LocalComercialRepository localRepository;
    private InstalacionRepository instalacionRepository;
    private TipoMantenimientoRepository tipoRepository;
    private ObligacionMantenimientoRepository obligacionRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private ObligacionAltaService servicio;
    private Centro centro;
    private LocalComercial local;
    private TipoMantenimiento tipo;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        centroRepository = mock(CentroRepository.class);
        localRepository = mock(LocalComercialRepository.class);
        instalacionRepository = mock(InstalacionRepository.class);
        tipoRepository = mock(TipoMantenimientoRepository.class);
        obligacionRepository = mock(ObligacionMantenimientoRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        DocumentoAutorizacionService autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        servicio = new ObligacionAltaService(centroRepository, localRepository, instalacionRepository, tipoRepository,
            obligacionRepository, ocupacionRepository, autorizacion, RELOJ);
        usuario(ADMIN, "ADMIN");
        usuario("explotacion@sintetico.test", "EXPLOTACION");
        usuario("inquilino@sintetico.test", "INQUILINO");
        usuario("proveedor@sintetico.test", "PROVEEDOR");
        centro = centro(CENTRO, true);
        local = local(LOCAL, centro, true);
        tipo = tipo(TIPO, 3, true);
        when(centroRepository.findParaAlta(CENTRO)).thenReturn(Optional.of(centro));
        when(localRepository.findById(LOCAL)).thenReturn(Optional.of(local));
        Instalacion instLocal = instalacion(INST_LOCAL, centro, local);
        Instalacion instZc = instalacion(INST_ZC, centro, null);
        when(instalacionRepository.findById(INST_LOCAL)).thenReturn(Optional.of(instLocal));
        when(instalacionRepository.findById(INST_ZC)).thenReturn(Optional.of(instZc));
        when(tipoRepository.findById(TIPO)).thenReturn(Optional.of(tipo));
        when(obligacionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void altaEnLocalCalculaLaProximaFechaYSoloCreaLaObligacion() {
        ObligacionCentroDto dto = servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 1, 15), null), ADMIN);
        assertThat(dto.proximaFecha()).isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(dto.localId()).isEqualTo(LOCAL);
        assertThat(dto.estado()).isEqualTo("ACTIVA");
        assertThat(dto.activo()).isTrue();
        verify(obligacionRepository).existeActivaEnLocal(LOCAL, 0L, TIPO);
        verify(obligacionRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void finDeMesYBisiesto() {
        when(tipo.getPeriodicidadMeses()).thenReturn(1);
        assertThat(servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 1, 31), null), ADMIN).proximaFecha())
            .isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2028, 1, 31), null), ADMIN).proximaFecha())
            .isEqualTo(LocalDate.of(2028, 2, 29));
        when(tipo.getPeriodicidadMeses()).thenReturn(12);
        assertThat(servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2028, 2, 29), null), ADMIN).proximaFecha())
            .isEqualTo(LocalDate.of(2029, 2, 28));
    }

    @Test
    void instalacionDeLocalSinLocalIdCompletaElLocal() {
        ObligacionCentroDto dto = servicio.crear(CENTRO, peticion(null, INST_LOCAL, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN);
        assertThat(dto.localId()).isEqualTo(LOCAL);
        assertThat(dto.instalacionId()).isEqualTo(INST_LOCAL);
        verify(obligacionRepository).existeActivaEnLocal(LOCAL, INST_LOCAL, TIPO);
    }

    @Test
    void zonaComunConInstalacionDeZonaComun() {
        ObligacionCentroDto dto = servicio.crear(CENTRO, peticion(null, INST_ZC, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN);
        assertThat(dto.localId()).isNull();
        verify(obligacionRepository).existeActivaEnZonaComun(CENTRO, INST_ZC, TIPO);
    }

    @Test
    void incoherenciasDeUbicacionDan400() {
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(LOCAL, INST_ZC, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
        LocalComercial otroLocal = local(101L, centro, true);
        when(localRepository.findById(101L)).thenReturn(Optional.of(otroLocal));
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(101L, INST_LOCAL, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
        Centro otro = centro(OTRO_CENTRO, true);
        LocalComercial localOtroCentro = local(102L, otro, true);
        when(localRepository.findById(102L)).thenReturn(Optional.of(localOtroCentro));
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(102L, null, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
        Instalacion instOtroCentro = instalacion(503L, otro, null);
        when(instalacionRepository.findById(503L)).thenReturn(Optional.of(instOtroCentro));
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(null, 503L, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
        verify(obligacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void inexistentesDan404EInactivos409() {
        assertThatThrownBy(() -> servicio.crear(99L, peticion(null, null, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(999L, null, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(null, 999L, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(RecursoNoEncontradoException.class);
        CrearObligacionRequest sinTipo = new CrearObligacionRequest(LOCAL, null, 999L, "NHOOD", LocalDate.of(2027, 1, 1), null, null);
        assertThatThrownBy(() -> servicio.crear(CENTRO, sinTipo, ADMIN)).isInstanceOf(RecursoNoEncontradoException.class);
        when(tipo.isActivo()).thenReturn(false);
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(ConflictoException.class);
        when(tipo.isActivo()).thenReturn(true);
        when(local.isActivo()).thenReturn(false);
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(ConflictoException.class);
        when(centro.isActivo()).thenReturn(false);
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(null, null, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(ConflictoException.class);
        verify(obligacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void responsableInquilinoD1() {
        // Zona comun: 400. Sin ocupacion vigente: 409. Con ocupacion vigente: alta.
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(null, null, "INQUILINO", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
        when(ocupacionRepository.existeOcupacionVigenteDelLocal(eq(LOCAL), any())).thenReturn(false);
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(LOCAL, null, "INQUILINO", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(ConflictoException.class);
        when(ocupacionRepository.existeOcupacionVigenteDelLocal(eq(LOCAL), any())).thenReturn(true);
        assertThat(servicio.crear(CENTRO, peticion(LOCAL, null, "INQUILINO", LocalDate.of(2027, 1, 1), null), ADMIN).responsable())
            .isEqualTo("INQUILINO");
        verify(ocupacionRepository, atLeastOnce()).existeOcupacionVigenteDelLocal(LOCAL, LocalDate.of(2026, 11, 23));
    }

    @Test
    void proximaFechaAnteriorAlInicioDa400YIgualSeAdmite() {
        assertThatThrownBy(() -> servicio.crear(CENTRO,
            peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 4, 30)), ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 1)), ADMIN)
            .proximaFecha()).isEqualTo(LocalDate.of(2027, 5, 1));
    }

    @Test
    void duplicadosDan409() {
        when(obligacionRepository.existeActivaEnLocal(LOCAL, 0L, TIPO)).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(ConflictoException.class);
        when(obligacionRepository.existeActivaEnZonaComun(CENTRO, 0L, TIPO)).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(null, null, "NHOOD", LocalDate.of(2027, 1, 1), null), ADMIN))
            .isInstanceOf(ConflictoException.class);
        verify(obligacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void permisos() {
        for (String email : new String[] {"inquilino@sintetico.test", "proveedor@sintetico.test"}) {
            assertThatThrownBy(() -> servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 1, 1), null), email))
                .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.listarTiposActivos(email)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.listarDelCentro(CENTRO, email)).isInstanceOf(AccessDeniedException.class);
        }
        verify(centroRepository, never()).findParaAlta(anyLong());
        assertThat(servicio.crear(CENTRO, peticion(LOCAL, null, "NHOOD", LocalDate.of(2027, 1, 1), null), "explotacion@sintetico.test"))
            .isNotNull();
        ArgumentCaptor<ObligacionMantenimiento> c = ArgumentCaptor.forClass(ObligacionMantenimiento.class);
        verify(obligacionRepository).saveAndFlush(c.capture());
        assertThat(c.getValue().getEstado()).isEqualTo(EstadoObligacion.ACTIVA);
    }

    private static CrearObligacionRequest peticion(Long localId, Long instalacionId, String responsable, LocalDate inicio, LocalDate proxima) {
        return new CrearObligacionRequest(localId, instalacionId, TIPO, responsable, inicio, proxima, null);
    }

    private void usuario(String email, String codigoRol) {
        Usuario u = mock(Usuario.class);
        Rol r = mock(Rol.class);
        when(r.getCodigo()).thenReturn(codigoRol);
        when(u.getRol()).thenReturn(r);
        when(u.getEmail()).thenReturn(email);
        when(usuarioRepository.findByEmailAndActivoTrue(email)).thenReturn(Optional.of(u));
    }

    private static Centro centro(Long id, boolean activo) {
        Centro c = mock(Centro.class);
        when(c.getId()).thenReturn(id);
        when(c.isActivo()).thenReturn(activo);
        return c;
    }

    private static LocalComercial local(Long id, Centro c, boolean activo) {
        LocalComercial l = mock(LocalComercial.class);
        when(l.getId()).thenReturn(id);
        when(l.getCentro()).thenReturn(c);
        when(l.isActivo()).thenReturn(activo);
        return l;
    }

    private static Instalacion instalacion(Long id, Centro c, LocalComercial l) {
        Instalacion i = mock(Instalacion.class);
        when(i.getId()).thenReturn(id);
        when(i.getCentro()).thenReturn(c);
        when(i.getLocal()).thenReturn(l);
        return i;
    }

    private static TipoMantenimiento tipo(Long id, int meses, boolean activo) {
        TipoMantenimiento t = mock(TipoMantenimiento.class);
        when(t.getId()).thenReturn(id);
        when(t.getPeriodicidadMeses()).thenReturn(meses);
        when(t.isActivo()).thenReturn(activo);
        when(t.getNombre()).thenReturn("Tipo sintetico");
        when(t.getReglaRecalculo()).thenReturn(ReglaRecalculo.DESDE_EJECUCION);
        return t;
    }

    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
