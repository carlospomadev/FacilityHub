package com.facilityhub.p6;

import com.facilityhub.api.dto.gestion.ResumenGestionDto;
import com.facilityhub.api.dto.gestion.TrabajoGestionDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.DocumentoAutorizacionService;
import com.facilityhub.service.PanelGestionService;
import com.facilityhub.service.ReglaVencimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

// FH-006 / P6-D3-1: pruebas unitarias (Mockito, sin BD) de la clasificacion del panel de gestion. Las exclusiones de
// APROBADO, CANCELADO y obligaciones no activas son de la consulta JPQL (abiertosHasta) y se prueban en PanelGestionP6D3P6IT.
class PanelGestionP6D3UnitTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);
    private static final int PREAVISO = 15;

    private MantenimientoRepository mantenimientoRepository;
    private IntervencionRepository intervencionRepository;
    private AsignacionMantenimientoRepository asignacionRepository;
    private ObligacionProveedorRepository declaracionRepository;
    private SolicitudProveedorRepository solicitudRepository;
    private DocumentoAutorizacionService autorizacion;
    private PanelGestionService servicio;
    private ObligacionMantenimiento conLocal, zonaComun;

    @BeforeEach
    void preparar() {
        mantenimientoRepository = mock(MantenimientoRepository.class);
        intervencionRepository = mock(IntervencionRepository.class);
        asignacionRepository = mock(AsignacionMantenimientoRepository.class);
        declaracionRepository = mock(ObligacionProveedorRepository.class);
        solicitudRepository = mock(SolicitudProveedorRepository.class);
        autorizacion = mock(DocumentoAutorizacionService.class);
        when(autorizacion.hoy()).thenReturn(HOY);
        servicio = new PanelGestionService(mantenimientoRepository, intervencionRepository, asignacionRepository,
            declaracionRepository, solicitudRepository, autorizacion, PREAVISO);

        Centro centro = mock(Centro.class);
        when(centro.getNombre()).thenReturn("Centro sintetico");
        TipoMantenimiento tipo = mock(TipoMantenimiento.class);
        when(tipo.getNombre()).thenReturn("Revision sintetica");
        LocalComercial local = mock(LocalComercial.class);
        when(local.getCodigo()).thenReturn("L-SINT");
        conLocal = obligacion(centro, tipo, local);
        zonaComun = obligacion(centro, tipo, null);
        when(intervencionRepository.bandeja(any())).thenReturn(List.of());
        when(intervencionRepository.findByMantenimientoIdInAndEstadoValidacionIn(anyCollection(), anyCollection())).thenReturn(List.of());
        when(asignacionRepository.activasDeMantenimientos(anyCollection())).thenReturn(List.of());
    }

    @Test
    void p6d3_ut_01_reglaVencimientoLimites() {
        assertThat(ReglaVencimiento.situacion(HOY, HOY, PREAVISO, false)).isEqualTo(ReglaVencimiento.Situacion.PROXIMO);
        assertThat(ReglaVencimiento.situacion(HOY.plusDays(15), HOY, PREAVISO, false)).isEqualTo(ReglaVencimiento.Situacion.PROXIMO);
        assertThat(ReglaVencimiento.situacion(HOY.plusDays(16), HOY, PREAVISO, false)).isEqualTo(ReglaVencimiento.Situacion.FUERA);
        assertThat(ReglaVencimiento.situacion(HOY.plusDays(3), HOY, PREAVISO, true)).isEqualTo(ReglaVencimiento.Situacion.FUERA);
        assertThat(ReglaVencimiento.situacion(HOY.minusDays(1), HOY, PREAVISO, false)).isEqualTo(ReglaVencimiento.Situacion.VENCIDO);
        assertThat(ReglaVencimiento.situacion(HOY.minusDays(1), HOY, PREAVISO, true))
            .isEqualTo(ReglaVencimiento.Situacion.VALIDACION_PENDIENTE_VENCIDA);
    }

    @Test
    void p6d3_ut_02_clasificacionDeLosCincoFiltros() {
        Mantenimiento vencido = mantenimiento(1L, conLocal, HOY.minusDays(3), EstadoMantenimiento.PROGRAMADO);
        Mantenimiento proximoAsignado = mantenimiento(2L, conLocal, HOY.plusDays(15), EstadoMantenimiento.PROGRAMADO);
        Mantenimiento enviado = mantenimiento(3L, conLocal, HOY.minusDays(4), EstadoMantenimiento.FINALIZADO);
        Mantenimiento corregir = mantenimiento(4L, conLocal, HOY.minusDays(2), EstadoMantenimiento.EN_PROCESO);
        Mantenimiento comun = mantenimiento(5L, zonaComun, HOY.minusDays(1), EstadoMantenimiento.PENDIENTE);
        when(mantenimientoRepository.abiertosHasta(HOY.plusDays(PREAVISO)))
            .thenReturn(List.of(enviado, vencido, corregir, comun, proximoAsignado));
        Intervencion pendiente = intervencion(30L, enviado, EstadoValidacion.PENDIENTE_VALIDACION);
        Intervencion correccion = intervencion(40L, corregir, EstadoValidacion.REQUIERE_CORRECCION);
        when(intervencionRepository.findByMantenimientoIdInAndEstadoValidacionIn(anyCollection(), anyCollection()))
            .thenReturn(List.of(pendiente, correccion));
        when(intervencionRepository.bandeja(EstadoValidacion.PENDIENTE_VALIDACION)).thenReturn(List.of(pendiente));
        when(intervencionRepository.bandeja(EstadoValidacion.REQUIERE_CORRECCION)).thenReturn(List.of(correccion));
        AsignacionMantenimiento asignacion = asignacion(proximoAsignado, "Proveedor sintetico");
        AsignacionMantenimiento asignacionCorregir = asignacion(corregir, "Proveedor corrector");
        when(asignacionRepository.activasDeMantenimientos(anyCollection())).thenReturn(List.of(asignacion, asignacionCorregir));

        // Enviado (FINALIZADO + PENDIENTE_VALIDACION): nunca vencido sin intervencion.
        assertThat(ids("VENCIDOS")).containsExactly(1L, 4L, 5L);
        assertThat(ids("PROXIMOS")).containsExactly(2L);
        assertThat(ids("PENDIENTE_VALIDACION")).containsExactly(3L);
        assertThat(ids("REQUIERE_CORRECCION")).containsExactly(4L);
        assertThat(ids("SIN_ASIGNAR")).containsExactly(1L, 5L);

        TrabajoGestionDto filaComun = servicio.trabajos("VENCIDOS", "explotacion").stream()
            .filter(t -> t.mantenimientoId() == 5L).findFirst().orElseThrow();
        assertThat(filaComun.zonaComun()).isTrue();
        assertThat(filaComun.local()).isNull();
        assertThat(filaComun.diasHastaVencimiento()).isEqualTo(-1);
        TrabajoGestionDto filaCorregir = servicio.trabajos("vencidos", "explotacion").stream()
            .filter(t -> t.mantenimientoId() == 4L).findFirst().orElseThrow();
        assertThat(filaCorregir.estadoIntervencion()).isEqualTo("REQUIERE_CORRECCION");
        assertThat(filaCorregir.proveedorAsignado()).isEqualTo("Proveedor corrector");
        assertThat(servicio.trabajos("PROXIMOS", "explotacion").get(0).proveedorAsignado()).isEqualTo("Proveedor sintetico");

        // Ventana de proximos = preaviso de P6-D2.
        verify(mantenimientoRepository, atLeastOnce()).abiertosHasta(HOY.plusDays(PREAVISO));
    }

    @Test
    void p6d3_ut_03_resumenCoherenteConLosListados() {
        Mantenimiento vencido = mantenimiento(1L, conLocal, HOY.minusDays(3), EstadoMantenimiento.PROGRAMADO);
        Mantenimiento proximo = mantenimiento(2L, zonaComun, HOY.plusDays(2), EstadoMantenimiento.PROGRAMADO);
        when(mantenimientoRepository.abiertosHasta(any())).thenReturn(List.of(vencido, proximo));
        ObligacionProveedor declaracion = mock(ObligacionProveedor.class);
        when(declaracionRepository.bandeja(EstadoDeclaracionProveedor.PROPUESTO)).thenReturn(List.of(declaracion, declaracion));
        when(solicitudRepository.findByEstadoOrderByIdAsc(EstadoSolicitudProveedor.PENDIENTE)).thenReturn(List.of(mock(SolicitudProveedor.class)));

        ResumenGestionDto r = servicio.resumen("explotacion");
        assertThat(r.fecha()).isEqualTo(HOY);
        assertThat(r.preavisoDias()).isEqualTo(PREAVISO);
        assertThat(r.vencidos()).isEqualTo(ids("VENCIDOS").size()).isEqualTo(1);
        assertThat(r.proximos()).isEqualTo(ids("PROXIMOS").size()).isEqualTo(1);
        assertThat(r.sinAsignar()).isEqualTo(ids("SIN_ASIGNAR").size()).isEqualTo(2);
        assertThat(r.pendientesValidacion()).isEqualTo(ids("PENDIENTE_VALIDACION").size()).isZero();
        assertThat(r.requierenCorreccion()).isEqualTo(ids("REQUIERE_CORRECCION").size()).isZero();
        assertThat(r.declaracionesPendientes()).isEqualTo(2);
        assertThat(r.solicitudesPendientes()).isEqualTo(1);
    }

    @Test
    void p6d3_ut_04_accesoYFiltroNoValido() {
        doThrow(new AccessDeniedException("Operacion no permitida")).when(autorizacion).exigirGestion(any());
        assertThatThrownBy(() -> servicio.resumen("inquilino")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.trabajos("VENCIDOS", "proveedor")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(mantenimientoRepository);

        doNothing().when(autorizacion).exigirGestion(any());
        assertThatThrownBy(() -> servicio.trabajos("OTRO", "explotacion")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.trabajos(null, "explotacion")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Utilidades ----

    private List<Long> ids(String filtro) {
        return servicio.trabajos(filtro, "explotacion").stream().map(TrabajoGestionDto::mantenimientoId).toList();
    }

    private static ObligacionMantenimiento obligacion(Centro centro, TipoMantenimiento tipo, LocalComercial local) {
        ObligacionMantenimiento o = mock(ObligacionMantenimiento.class);
        when(o.getCentro()).thenReturn(centro);
        when(o.getTipoMantenimiento()).thenReturn(tipo);
        when(o.getLocal()).thenReturn(local);
        when(o.getResponsable()).thenReturn(ResponsableObligacion.NHOOD);
        return o;
    }

    private static Mantenimiento mantenimiento(Long id, ObligacionMantenimiento o, LocalDate fecha, EstadoMantenimiento estado) {
        Mantenimiento m = mock(Mantenimiento.class);
        when(m.getId()).thenReturn(id);
        when(m.getObligacion()).thenReturn(o);
        when(m.getFechaProgramada()).thenReturn(fecha);
        when(m.getEstado()).thenReturn(estado);
        when(m.admiteAsignacion()).thenReturn(estado == EstadoMantenimiento.PROGRAMADO
            || estado == EstadoMantenimiento.PENDIENTE || estado == EstadoMantenimiento.EN_PROCESO);
        return m;
    }

    private static Intervencion intervencion(Long id, Mantenimiento m, EstadoValidacion estado) {
        Intervencion i = mock(Intervencion.class);
        when(i.getId()).thenReturn(id);
        when(i.getMantenimiento()).thenReturn(m);
        when(i.getEstadoValidacion()).thenReturn(estado);
        return i;
    }

    private static AsignacionMantenimiento asignacion(Mantenimiento m, String razonSocial) {
        Proveedor p = mock(Proveedor.class);
        when(p.getRazonSocial()).thenReturn(razonSocial);
        AsignacionMantenimiento a = mock(AsignacionMantenimiento.class);
        when(a.getMantenimiento()).thenReturn(m);
        when(a.getProveedor()).thenReturn(p);
        return a;
    }
}
