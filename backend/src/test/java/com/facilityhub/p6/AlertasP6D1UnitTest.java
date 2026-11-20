package com.facilityhub.p6;

import com.facilityhub.domain.*;
import com.facilityhub.repository.AlertaRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.UsuarioRepository;
import com.facilityhub.service.AlertaService;
import com.facilityhub.service.DocumentoAutorizacionService;
import com.facilityhub.service.RecursoNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FH-006 / P6-D1: pruebas unitarias (Mockito, sin BD) de los destinatarios, la idempotencia y el aislamiento de alertas.
class AlertasP6D1UnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);
    private static final Long LOCAL = 10L, PROV = 60L, INQ = 20L, INQ_ANTERIOR = 21L;

    private AlertaRepository alertaRepository;
    private UsuarioRepository usuarioRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private AlertaService alertas;

    private Usuario tec1, tec2, expl1, expl2, inq, inqAnterior;
    private LocalComercial local;
    private ObligacionMantenimiento obligacion;
    private Mantenimiento mantenimiento;
    private Proveedor proveedor;
    private OcupacionLocal ocupacionVigente;

    @BeforeEach
    void preparar() {
        alertaRepository = mock(AlertaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        DocumentoAutorizacionService autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        alertas = new AlertaService(alertaRepository, usuarioRepository, ocupacionRepository, autorizacion, RELOJ);
        when(alertaRepository.insertarSiNoExiste(anyLong(), anyString(), anyString(), any(), any(), any(), anyString(), any())).thenReturn(1);

        tec1 = usuario(1L, "tec1@sintetico.test"); tec2 = usuario(2L, "tec2@sintetico.test");
        expl1 = usuario(3L, "expl1@sintetico.test"); expl2 = usuario(4L, "expl2@sintetico.test");
        inq = usuario(5L, "inq@sintetico.test"); inqAnterior = usuario(6L, "inq.ant@sintetico.test");
        when(usuarioRepository.tecnicosActivos(PROV)).thenReturn(List.of(tec1, tec2));
        when(usuarioRepository.activosConRol("EXPLOTACION")).thenReturn(List.of(expl1, expl2));
        when(usuarioRepository.usuariosActivosDeInquilino(INQ)).thenReturn(List.of(inq));
        when(usuarioRepository.usuariosActivosDeInquilino(INQ_ANTERIOR)).thenReturn(List.of(inqAnterior));

        local = mock(LocalComercial.class);
        when(local.getId()).thenReturn(LOCAL);
        when(local.getCodigo()).thenReturn("L-SINT");
        TipoMantenimiento tipo = mock(TipoMantenimiento.class);
        when(tipo.getNombre()).thenReturn("Revision sintetica");
        obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getId()).thenReturn(40L);
        when(obligacion.getLocal()).thenReturn(local);
        when(obligacion.getTipoMantenimiento()).thenReturn(tipo);
        mantenimiento = spy(new Mantenimiento(obligacion, LocalDate.of(2026, 12, 15), "Sintetico"));
        doReturn(50L).when(mantenimiento).getId();
        proveedor = mock(Proveedor.class);
        when(proveedor.getId()).thenReturn(PROV);

        Inquilino inquilino = mock(Inquilino.class);
        when(inquilino.getId()).thenReturn(INQ);
        ocupacionVigente = mock(OcupacionLocal.class);
        when(ocupacionVigente.getId()).thenReturn(30L);
        when(ocupacionVigente.getInquilino()).thenReturn(inquilino);
        when(ocupacionVigente.getFechaInicio()).thenReturn(LocalDate.of(2026, 1, 1));
        when(ocupacionRepository.ocupacionesVigentesDelLocal(LOCAL, HOY)).thenReturn(List.of(ocupacionVigente));
    }

    @Test
    void p6d1_01_asignacionAvisaATodosLosTecnicosActivosDeLaEmpresa() {
        AsignacionMantenimiento a = mock(AsignacionMantenimiento.class);
        when(a.getId()).thenReturn(7L);
        when(a.getMantenimiento()).thenReturn(mantenimiento);
        when(a.getProveedor()).thenReturn(proveedor);
        assertThat(alertas.asignacion(a, expl1)).isEqualTo(2);
        verify(alertaRepository).insertarSiNoExiste(eq(1L), eq("ASIGNACION"), eq("asignacion:7"), eq(50L), eq(40L), isNull(), anyString(), any());
        verify(alertaRepository).insertarSiNoExiste(eq(2L), eq("ASIGNACION"), eq("asignacion:7"), eq(50L), eq(40L), isNull(), anyString(), any());
    }

    @Test
    void p6d1_02_declaracionAvisaAExplotacionSinIncluirAlAutor() {
        ObligacionProveedor d = mock(ObligacionProveedor.class);
        when(d.getId()).thenReturn(9L);
        when(d.getObligacion()).thenReturn(obligacion);
        assertThat(alertas.proveedorDeclarado(d, inq)).isEqualTo(2);
        // Un usuario de EXPLOTACION que fuese el autor no se avisa a si mismo.
        alertas.proveedorDeclarado(d, expl1);
        verify(alertaRepository, times(1)).insertarSiNoExiste(eq(3L), eq("PROVEEDOR_DECLARADO"), eq("obligacion_proveedor:9"), isNull(), eq(40L), isNull(), anyString(), any());
        verify(alertaRepository, times(2)).insertarSiNoExiste(eq(4L), eq("PROVEEDOR_DECLARADO"), eq("obligacion_proveedor:9"), isNull(), eq(40L), isNull(), anyString(), any());
    }

    @Test
    void p6d1_03_envioCorreccionYAprobacionConEventoPorEnvio() {
        Intervencion i = intervencion(2);
        assertThat(alertas.evidenciasPresentadas(i, tec1)).isEqualTo(2);
        verify(alertaRepository).insertarSiNoExiste(eq(3L), eq("EVIDENCIAS_PRESENTADAS"), eq("intervencion:70:envio:2"), eq(50L), eq(40L), eq(70L), anyString(), any());
        assertThat(alertas.correccionSolicitada(i, expl1)).isEqualTo(2);
        verify(alertaRepository).insertarSiNoExiste(eq(1L), eq("CORRECCION_SOLICITADA"), eq("intervencion:70:envio:2:correccion"), any(), any(), any(), anyString(), any());
        // Aprobacion: 2 tecnicos + el inquilino de la ocupacion vigente que cubre el mantenimiento.
        assertThat(alertas.aprobado(i, expl1)).isEqualTo(3);
        verify(alertaRepository).insertarSiNoExiste(eq(5L), eq("APROBADO"), eq("intervencion:70:envio:2:aprobada"), any(), any(), any(), anyString(), any());
    }

    @Test
    void p6d1_04_aprobacionNoAvisaAOcupantesAnterioresNiEnZonaComun() {
        Intervencion i = intervencion(1);
        // Mantenimiento vinculado a una ocupacion anterior (ya no vigente): el inquilino actual no lo cubre.
        OcupacionLocal anterior = mock(OcupacionLocal.class);
        when(anterior.getId()).thenReturn(31L);
        mantenimiento.vincularOcupacion(anterior, LocalDateTime.now(RELOJ));
        assertThat(alertas.aprobado(i, expl1)).isEqualTo(2); // solo los tecnicos
        verify(alertaRepository, never()).insertarSiNoExiste(eq(5L), anyString(), anyString(), any(), any(), any(), anyString(), any());
        verify(alertaRepository, never()).insertarSiNoExiste(eq(6L), anyString(), anyString(), any(), any(), any(), anyString(), any());
        // Zona comun: sin inquilino.
        when(obligacion.getLocal()).thenReturn(null);
        Mantenimiento zc = spy(new Mantenimiento(obligacion, LocalDate.of(2026, 12, 15), "Zona comun"));
        doReturn(51L).when(zc).getId();
        Intervencion izc = intervencion(1);
        when(izc.getMantenimiento()).thenReturn(zc);
        assertThat(alertas.aprobado(izc, expl1)).isEqualTo(2);
    }

    @Test
    void p6d1_05_idempotenciaYMensajeSinDatosPersonales() {
        when(alertaRepository.insertarSiNoExiste(anyLong(), anyString(), anyString(), any(), any(), any(), anyString(), any())).thenReturn(0);
        Intervencion i = intervencion(1);
        assertThat(alertas.evidenciasPresentadas(i, tec1)).isZero(); // ya existian: no se duplican ni fallan
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(alertaRepository, atLeastOnce()).insertarSiNoExiste(anyLong(), anyString(), anyString(), any(), any(), any(), mensaje.capture(), any());
        assertThat(mensaje.getValue()).contains("Revision sintetica").contains("L-SINT").doesNotContain("@");
    }

    @Test
    void p6d1_06_cadaUsuarioSoloLeeYMarcaSusAlertas() {
        when(usuarioRepository.findByEmailAndActivoTrue("tec1@sintetico.test")).thenReturn(Optional.of(tec1));
        when(alertaRepository.findByIdAndDestinatarioId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> alertas.marcarLeida(99L, "tec1@sintetico.test")).isInstanceOf(RecursoNoDisponibleException.class);
        alertas.propias("tec1@sintetico.test", true);
        verify(alertaRepository).findTop100ByDestinatarioIdAndLeidaAtIsNullOrderByCreatedAtDescIdDesc(1L);
        alertas.noLeidas("tec1@sintetico.test");
        verify(alertaRepository).countByDestinatarioIdAndLeidaAtIsNull(1L);
    }

    private Intervencion intervencion(int envio) {
        Intervencion i = mock(Intervencion.class);
        when(i.getId()).thenReturn(70L);
        when(i.getNumeroEnvio()).thenReturn(envio);
        when(i.getMantenimiento()).thenReturn(mantenimiento);
        when(i.getProveedor()).thenReturn(proveedor);
        return i;
    }

    private static Usuario usuario(Long id, String email) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(id);
        when(u.getEmail()).thenReturn(email);
        when(u.isActivo()).thenReturn(true);
        return u;
    }
}
