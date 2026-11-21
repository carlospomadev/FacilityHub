package com.facilityhub.p6;

import com.facilityhub.api.dto.alerta.ResultadoRecordatoriosDto;
import com.facilityhub.config.RecordatoriosProgramados;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FH-006 / P6-D2: pruebas unitarias (Mockito, sin BD) de las reglas, destinatarios, deduplicacion y control de ejecucion
// de los recordatorios de vencimiento.
class RecordatoriosP6D2UnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);
    private static final Long LOCAL = 10L, PROV = 60L, INQ = 20L;

    private MantenimientoRepository mantenimientoRepository;
    private IntervencionRepository intervencionRepository;
    private AsignacionMantenimientoRepository asignacionRepository;
    private UsuarioRepository usuarioRepository;
    private AlertaRepository alertaRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private AuditoriaService auditoria;
    private RecordatorioService servicio;

    private Usuario tec1, expl1, inq;
    private ObligacionMantenimiento obligacion;
    private AsignacionMantenimiento asignacion;

    @BeforeEach
    void preparar() {
        mantenimientoRepository = mock(MantenimientoRepository.class);
        intervencionRepository = mock(IntervencionRepository.class);
        asignacionRepository = mock(AsignacionMantenimientoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        alertaRepository = mock(AlertaRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        auditoria = mock(AuditoriaService.class);
        servicio = servicio(15, 7, 90);
        when(alertaRepository.bloquearEjecucion(anyLong())).thenReturn(true);
        when(alertaRepository.insertarSiNoExiste(anyLong(), anyString(), anyString(), any(), any(), any(), anyString(), any())).thenReturn(1);
        when(intervencionRepository.findFirstByMantenimientoIdAndEstadoValidacionIn(anyLong(), any())).thenReturn(Optional.empty());

        tec1 = usuario(1L); expl1 = usuario(3L); inq = usuario(5L);
        when(usuarioRepository.tecnicosActivos(PROV)).thenReturn(List.of(tec1));
        when(usuarioRepository.activosConRol("EXPLOTACION")).thenReturn(List.of(expl1));
        when(usuarioRepository.usuariosActivosDeInquilino(INQ)).thenReturn(List.of(inq));

        LocalComercial local = mock(LocalComercial.class);
        when(local.getId()).thenReturn(LOCAL);
        when(local.getCodigo()).thenReturn("L-SINT");
        TipoMantenimiento tipo = mock(TipoMantenimiento.class);
        when(tipo.getNombre()).thenReturn("Revision sintetica");
        obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getId()).thenReturn(40L);
        when(obligacion.getLocal()).thenReturn(local);
        when(obligacion.getTipoMantenimiento()).thenReturn(tipo);
        when(obligacion.getResponsable()).thenReturn(ResponsableObligacion.NHOOD);

        Proveedor proveedor = mock(Proveedor.class);
        when(proveedor.getId()).thenReturn(PROV);
        asignacion = mock(AsignacionMantenimiento.class);
        when(asignacion.getProveedor()).thenReturn(proveedor);

        Inquilino inquilino = mock(Inquilino.class);
        when(inquilino.getId()).thenReturn(INQ);
        OcupacionLocal ocupacion = mock(OcupacionLocal.class);
        when(ocupacion.getId()).thenReturn(30L);
        when(ocupacion.getInquilino()).thenReturn(inquilino);
        when(ocupacion.getFechaInicio()).thenReturn(LocalDate.of(2026, 1, 1));
        when(ocupacionRepository.ocupacionesVigentesDelLocal(LOCAL, HOY)).thenReturn(List.of(ocupacion));
    }

    @Test
    void p6d2_01_limitesDelPreaviso() {
        assertThat(tipo(HOY, null)).isEqualTo(TipoAlerta.VENCIMIENTO_PROXIMO);
        assertThat(tipo(HOY.plusDays(15), null)).isEqualTo(TipoAlerta.VENCIMIENTO_PROXIMO);
        assertThat(tipo(HOY.plusDays(16), null)).isNull();
        // Intervencion ya presentada y pendiente de validar: no se recuerda el vencimiento proximo.
        assertThat(tipo(HOY.plusDays(5), intervencion(70L))).isNull();
        // El preaviso es configurable.
        RecordatorioService preaviso30 = servicio(30, 7, 90);
        Mantenimiento lejano = mantenimiento(50L, HOY.plusDays(25));
        when(mantenimientoRepository.candidatosRecordatorio(any(), any())).thenReturn(List.of(lejano));
        when(intervencionRepository.findFirstByMantenimientoIdAndEstadoValidacionIn(eq(50L), any())).thenReturn(Optional.empty());
        assertThat(preaviso30.ejecutar(true).proximos()).isEqualTo(1);
        verify(mantenimientoRepository).candidatosRecordatorio(HOY.minusDays(90), HOY.plusDays(30));
    }

    @Test
    void p6d2_02_vencidosPorBloquesYValidacionPendiente() {
        assertThat(ref(HOY.minusDays(1), null)).isEqualTo("vencido:50:" + HOY.minusDays(1) + ":b0");
        assertThat(ref(HOY.minusDays(7), null)).endsWith(":b0");
        assertThat(ref(HOY.minusDays(8), null)).endsWith(":b1");
        assertThat(tipo(HOY.minusDays(90), null)).isEqualTo(TipoAlerta.MANTENIMIENTO_VENCIDO);
        assertThat(tipo(HOY.minusDays(91), null)).isNull(); // antiguedad maxima
        // Vencido con evidencias pendientes de validar: tipo distinto (documentacion pendiente, no incumplimiento).
        assertThat(tipo(HOY.minusDays(3), intervencion(70L))).isEqualTo(TipoAlerta.VALIDACION_PENDIENTE_VENCIDA);
        assertThat(ref(HOY.minusDays(3), intervencion(70L))).isEqualTo("valpend:50:70:b0");
    }

    @Test
    void p6d2_03_destinatariosNhoodConYSinAsignacion() {
        Mantenimiento m = mantenimiento(50L, HOY.minusDays(2));
        when(asignacionRepository.findByMantenimientoIdAndActivaTrue(50L)).thenReturn(Optional.of(asignacion));
        when(mantenimientoRepository.candidatosRecordatorio(any(), any())).thenReturn(List.of(m));
        assertThat(servicio.ejecutar(false).avisos()).isEqualTo(2);
        verify(alertaRepository).insertarSiNoExiste(eq(1L), eq("MANTENIMIENTO_VENCIDO"), anyString(), eq(50L), eq(40L), isNull(), anyString(), any());
        verify(alertaRepository).insertarSiNoExiste(eq(3L), eq("MANTENIMIENTO_VENCIDO"), anyString(), eq(50L), eq(40L), isNull(), anyString(), any());
        // Sin asignacion activa: solo EXPLOTACION; nunca el inquilino en una obligacion NHOOD.
        when(asignacionRepository.findByMantenimientoIdAndActivaTrue(50L)).thenReturn(Optional.empty());
        assertThat(servicio.ejecutar(false).avisos()).isEqualTo(1);
        verify(alertaRepository, never()).insertarSiNoExiste(eq(5L), anyString(), anyString(), any(), any(), any(), anyString(), any());
    }

    @Test
    void p6d2_04_destinatariosInquilinoYValidacionPendiente() {
        when(obligacion.getResponsable()).thenReturn(ResponsableObligacion.INQUILINO);
        Mantenimiento m = mantenimiento(50L, HOY.plusDays(10));
        when(asignacionRepository.findByMantenimientoIdAndActivaTrue(50L)).thenReturn(Optional.of(asignacion));
        when(mantenimientoRepository.candidatosRecordatorio(any(), any())).thenReturn(List.of(m));
        ResultadoRecordatoriosDto r = servicio.ejecutar(false);
        assertThat(r.proximos()).isEqualTo(1);
        assertThat(r.avisos()).isEqualTo(3); // EXPLOTACION + tecnico + inquilino
        verify(alertaRepository).insertarSiNoExiste(eq(5L), eq("VENCIMIENTO_PROXIMO"), eq("vence:50:" + HOY.plusDays(10) + ":p15"),
            eq(50L), eq(40L), isNull(), anyString(), any());
        // Vencido y pendiente de validar: EXPLOTACION e inquilino; el proveedor ya presento la evidencia.
        Mantenimiento v = mantenimiento(51L, HOY.minusDays(4));
        Intervencion i = intervencion(71L);
        when(intervencionRepository.findFirstByMantenimientoIdAndEstadoValidacionIn(eq(51L), any())).thenReturn(Optional.of(i));
        when(mantenimientoRepository.candidatosRecordatorio(any(), any())).thenReturn(List.of(v));
        when(asignacionRepository.findByMantenimientoIdAndActivaTrue(51L)).thenReturn(Optional.of(asignacion));
        assertThat(servicio.ejecutar(false).avisos()).isEqualTo(2);
        verify(alertaRepository, never()).insertarSiNoExiste(eq(1L), eq("VALIDACION_PENDIENTE_VENCIDA"), anyString(), any(), any(), any(), anyString(), any());
        verify(alertaRepository).insertarSiNoExiste(eq(3L), eq("VALIDACION_PENDIENTE_VENCIDA"), eq("valpend:51:71:b0"), eq(51L), eq(40L), eq(71L), anyString(), any());
    }

    @Test
    void p6d2_05_idempotenciaYSimulacionSinEscrituras() {
        Mantenimiento m = mantenimiento(50L, HOY.minusDays(1));
        when(mantenimientoRepository.candidatosRecordatorio(any(), any())).thenReturn(List.of(m));
        // Segunda ejecucion: los avisos ya existen y no se duplican.
        when(alertaRepository.insertarSiNoExiste(anyLong(), anyString(), anyString(), any(), any(), any(), anyString(), any())).thenReturn(0);
        assertThat(servicio.ejecutar(false).avisos()).isZero();
        // Simulacion: cuenta solo los que faltan y no inserta nada.
        clearInvocations(alertaRepository);
        when(alertaRepository.existsByDestinatarioIdAndTipoAndEventoRef(eq(3L), eq(TipoAlerta.MANTENIMIENTO_VENCIDO), anyString())).thenReturn(false);
        ResultadoRecordatoriosDto r = servicio.ejecutar(true);
        assertThat(r.simulacion()).isTrue();
        assertThat(r.avisos()).isEqualTo(1);
        verify(alertaRepository, never()).insertarSiNoExiste(anyLong(), anyString(), anyString(), any(), any(), any(), anyString(), any());
        // Mensaje sin datos personales.
        clearInvocations(alertaRepository);
        when(alertaRepository.insertarSiNoExiste(anyLong(), anyString(), anyString(), any(), any(), any(), anyString(), any())).thenReturn(1);
        servicio.ejecutar(false);
        verify(alertaRepository).insertarSiNoExiste(anyLong(), anyString(), anyString(), any(), any(), any(),
            argThat(t -> t.contains("Revision sintetica") && t.contains("L-SINT") && !t.contains("@")), any());
    }

    @Test
    void p6d2_06_ejecucionConcurrenteRechazadaYAuditoriaManual() {
        when(alertaRepository.bloquearEjecucion(anyLong())).thenReturn(false);
        assertThatThrownBy(() -> servicio.ejecutar(false)).isInstanceOf(ConflictoException.class);
        verifyNoInteractions(mantenimientoRepository);
        // Ejecucion manual real: auditada; la simulacion no.
        when(alertaRepository.bloquearEjecucion(anyLong())).thenReturn(true);
        when(usuarioRepository.findByEmailAndActivoTrue("admin@sintetico.test")).thenReturn(Optional.of(expl1));
        servicio.ejecutarManual(true, "admin@sintetico.test");
        verify(auditoria, never()).registrar(anyString(), anyLong(), anyString(), any(), any());
        servicio.ejecutarManual(false, "admin@sintetico.test");
        verify(auditoria).registrar(eq("RECORDATORIOS"), eq(0L), eq("EJECUTAR"), eq(expl1), any());
    }

    @Test
    void p6d2_07_tareaProgramadaDesactivadaPorDefectoYConfiguracionValida() {
        ConditionalOnProperty c = RecordatoriosProgramados.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(c.havingValue()).isEqualTo("true");
        assertThat(c.matchIfMissing()).isFalse();
        assertThatThrownBy(() -> servicio(15, 0, 90)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio(-1, 7, 90)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- utilidades ----------

    private RecordatorioService servicio(int preaviso, int cadencia, int antiguedad) {
        DocumentoAutorizacionService autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        AlertaService alertas = new AlertaService(alertaRepository, usuarioRepository, ocupacionRepository, autorizacion, RELOJ);
        return new RecordatorioService(mantenimientoRepository, intervencionRepository, asignacionRepository, usuarioRepository,
            alertaRepository, alertas, autorizacion, auditoria, RELOJ, preaviso, cadencia, antiguedad);
    }

    // Clasifica un unico mantenimiento a traves de una ejecucion simulada y devuelve el tipo (o null si no hay aviso).
    private TipoAlerta tipo(LocalDate fecha, Intervencion enValidacion) {
        String r = ref(fecha, enValidacion);
        if (r == null) return null;
        if (r.startsWith("vence:")) return TipoAlerta.VENCIMIENTO_PROXIMO;
        if (r.startsWith("valpend:")) return TipoAlerta.VALIDACION_PENDIENTE_VENCIDA;
        return TipoAlerta.MANTENIMIENTO_VENCIDO;
    }

    private String ref(LocalDate fecha, Intervencion enValidacion) {
        clearInvocations(alertaRepository);
        Mantenimiento m = mantenimiento(50L, fecha);
        when(mantenimientoRepository.candidatosRecordatorio(any(), any())).thenReturn(List.of(m));
        when(intervencionRepository.findFirstByMantenimientoIdAndEstadoValidacionIn(eq(50L), any()))
            .thenReturn(Optional.ofNullable(enValidacion));
        servicio.ejecutar(false);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(alertaRepository, atMost(10)).insertarSiNoExiste(anyLong(), anyString(), captor.capture(), any(), any(), any(), anyString(), any());
        return captor.getAllValues().isEmpty() ? null : captor.getValue();
    }

    private Mantenimiento mantenimiento(Long id, LocalDate fecha) {
        Mantenimiento m = spy(new Mantenimiento(obligacion, fecha, "Sintetico"));
        doReturn(id).when(m).getId();
        return m;
    }

    private static Intervencion intervencion(Long id) {
        Intervencion i = mock(Intervencion.class);
        when(i.getId()).thenReturn(id);
        return i;
    }

    private static Usuario usuario(Long id) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(id);
        when(u.isActivo()).thenReturn(true);
        return u;
    }
}
