package com.facilityhub.p6;

import com.facilityhub.api.dto.historial.HistorialGestionDto;
import com.facilityhub.api.dto.historial.HistorialInquilinoDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.DocumentoAutorizacionService;
import com.facilityhub.service.HistorialService;
import com.facilityhub.service.RecursoNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FH-006 / P6-D3-2: pruebas unitarias (Mockito, sin BD) del historial del local: tipos de registro de gestion,
// historicos P5, filtrado del inquilino (ocupacion, aprobados, certificados compartidos) y control de acceso.
class HistorialP6D3UnitTest {
    private static final Long LOCAL = 10L;

    private LocalComercialRepository localRepository;
    private MantenimientoRepository mantenimientoRepository;
    private IntervencionRepository intervencionRepository;
    private DocumentoMantenimientoRepository documentoRepository;
    private DocumentoAutorizacionService autorizacion;
    private HistorialService servicio;
    private ObligacionMantenimiento obligacion;
    private Usuario gestion, inquilino, proveedor;
    private OcupacionLocal ocupacion;

    @BeforeEach
    void preparar() {
        localRepository = mock(LocalComercialRepository.class);
        mantenimientoRepository = mock(MantenimientoRepository.class);
        intervencionRepository = mock(IntervencionRepository.class);
        documentoRepository = mock(DocumentoMantenimientoRepository.class);
        autorizacion = mock(DocumentoAutorizacionService.class);
        servicio = new HistorialService(localRepository, mantenimientoRepository, intervencionRepository, documentoRepository, autorizacion);

        gestion = mock(Usuario.class);
        inquilino = mock(Usuario.class);
        proveedor = mock(Usuario.class);
        when(autorizacion.usuarioActual("gestion")).thenReturn(gestion);
        when(autorizacion.usuarioActual("inquilino")).thenReturn(inquilino);
        when(autorizacion.usuarioActual("proveedor")).thenReturn(proveedor);
        when(autorizacion.esGestion(gestion)).thenReturn(true);
        when(autorizacion.esProveedor(proveedor)).thenReturn(true);
        when(localRepository.existsById(LOCAL)).thenReturn(true);
        when(autorizacion.puedeAccederLocal(gestion, LOCAL)).thenReturn(true);
        when(autorizacion.puedeAccederLocal(inquilino, LOCAL)).thenReturn(true);
        ocupacion = mock(OcupacionLocal.class);
        when(ocupacion.getFechaInicio()).thenReturn(LocalDate.of(2026, 1, 1));
        when(autorizacion.ocupacionVigente(inquilino, LOCAL)).thenReturn(Optional.of(ocupacion));

        TipoMantenimiento tipo = mock(TipoMantenimiento.class);
        when(tipo.getNombre()).thenReturn("Revision sintetica");
        obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getId()).thenReturn(40L);
        when(obligacion.getTipoMantenimiento()).thenReturn(tipo);
        when(obligacion.getResponsable()).thenReturn(ResponsableObligacion.NHOOD);
        when(documentoRepository.findByMantenimientoIdInOrderByFechaSubidaAscIdAsc(anyCollection())).thenReturn(List.of());
        when(documentoRepository.findByIntervencionIdInOrderByFechaSubidaAscIdAsc(anyCollection())).thenReturn(List.of());
    }

    @Test
    void p6d3_ut_10_gestionAprobadoCerradoYHistoricoP5() {
        Mantenimiento aprobado = mantenimiento(1L, true, LocalDate.of(2026, 10, 1), EstadoMantenimiento.FINALIZADO);
        Mantenimiento cerrado = mantenimiento(2L, true, LocalDate.of(2026, 10, 20), EstadoMantenimiento.PROGRAMADO);
        Mantenimiento p5 = mantenimiento(3L, false, LocalDate.of(2026, 3, 1), EstadoMantenimiento.FINALIZADO);
        Mantenimiento pendiente = mantenimiento(4L, true, LocalDate.of(2026, 11, 1), EstadoMantenimiento.FINALIZADO);
        // P6-D3-6: un P5 abierto (PROGRAMADO, fecha futura) es trabajo pendiente, no historial.
        Mantenimiento p5Abierto = mantenimiento(5L, false, LocalDate.of(2027, 1, 15), EstadoMantenimiento.PROGRAMADO);
        Mantenimiento p5Cancelado = mantenimiento(6L, false, LocalDate.of(2026, 2, 1), EstadoMantenimiento.CANCELADO);
        when(mantenimientoRepository.delLocal(LOCAL)).thenReturn(List.of(p5Abierto, pendiente, cerrado, aprobado, p5, p5Cancelado));
        Intervencion iAprobada = intervencion(31L, aprobado, EstadoValidacion.APROBADO, LocalDate.of(2026, 10, 1));
        Intervencion iCerrada = intervencion(32L, cerrado, EstadoValidacion.CERRADO_SIN_CUMPLIMIENTO, LocalDate.of(2026, 10, 20));
        when(intervencionRepository.historialDeMantenimientos(anyCollection(), anyCollection())).thenReturn(List.of(iCerrada, iAprobada));
        DocumentoMantenimiento interno = documento(51L, aprobado, iAprobada, CategoriaDocumento.EVIDENCIA, VisibilidadDocumento.INTERNO);
        DocumentoMantenimiento compartido = documento(52L, aprobado, iAprobada, CategoriaDocumento.CERTIFICADO, VisibilidadDocumento.COMPARTIDO);
        DocumentoMantenimiento legado = documento(53L, p5, null, null, VisibilidadDocumento.COMPARTIDO);
        when(documentoRepository.findByMantenimientoIdInOrderByFechaSubidaAscIdAsc(anyCollection()))
            .thenReturn(List.of(interno, compartido, legado));

        @SuppressWarnings("unchecked")
        List<HistorialGestionDto> h = (List<HistorialGestionDto>) servicio.historial(LOCAL, "gestion");

        // Mas reciente primero; el pendiente (sin intervencion cerrada) no es historial.
        assertThat(h).extracting(HistorialGestionDto::tipoRegistro)
            .containsExactly(HistorialService.CERRADO_SIN_CUMPLIMIENTO, HistorialService.APROBADO, HistorialService.HISTORICO_P5,
                HistorialService.HISTORICO_P5);
        assertThat(h).extracting(HistorialGestionDto::mantenimientoId).containsExactly(2L, 1L, 3L, 6L).doesNotContain(5L);
        assertThat(h.get(3).estadoMantenimiento()).isEqualTo("CANCELADO");
        HistorialGestionDto a = h.get(1);
        assertThat(a.intervencionId()).isEqualTo(31L);
        assertThat(a.estadoValidacion()).isEqualTo("APROBADO");
        assertThat(a.documentos()).extracting(d -> d.id()).containsExactly(51L, 52L); // gestion ve tambien los internos
        HistorialGestionDto historico = h.get(2);
        assertThat(historico.etiqueta()).isEqualTo("Histórico P5 — sin validación");
        assertThat(historico.intervencionId()).isNull();
        assertThat(historico.estadoValidacion()).isNull(); // nunca se presenta como aprobado
        assertThat(historico.estadoMantenimiento()).isEqualTo("FINALIZADO"); // estado real conservado
        assertThat(historico.documentos()).extracting(d -> d.id()).containsExactly(53L);
        assertThat(h.get(0).etiqueta()).isEqualTo("Cerrado sin cumplimiento");
        assertThat(h).extracting(HistorialGestionDto::mantenimientoId).doesNotContain(4L, 5L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void p6d3_ut_11_inquilinoSoloAprobadosDeSuOcupacionYCertificadosCompartidos() {
        Mantenimiento propio = mantenimiento(1L, true, LocalDate.of(2026, 10, 1), EstadoMantenimiento.FINALIZADO);
        Mantenimiento anterior = mantenimiento(2L, true, LocalDate.of(2025, 6, 1), EstadoMantenimiento.FINALIZADO);
        Mantenimiento p5 = mantenimiento(3L, false, LocalDate.of(2026, 3, 1), EstadoMantenimiento.FINALIZADO);
        when(mantenimientoRepository.delLocal(LOCAL)).thenReturn(List.of(propio, anterior, p5));
        Intervencion iPropia = intervencion(31L, propio, EstadoValidacion.APROBADO, LocalDate.of(2026, 10, 1));
        when(intervencionRepository.historialDeMantenimientos(anyCollection(), anyCollection())).thenReturn(List.of(iPropia));
        DocumentoMantenimiento compartido = documento(52L, propio, iPropia, CategoriaDocumento.CERTIFICADO, VisibilidadDocumento.COMPARTIDO);
        DocumentoMantenimiento certInterno = documento(54L, propio, iPropia, CategoriaDocumento.CERTIFICADO, VisibilidadDocumento.INTERNO);
        DocumentoMantenimiento evidencia = documento(51L, propio, iPropia, CategoriaDocumento.EVIDENCIA, VisibilidadDocumento.INTERNO);
        when(documentoRepository.findByIntervencionIdInOrderByFechaSubidaAscIdAsc(anyCollection()))
            .thenReturn(List.of(evidencia, compartido, certInterno));

        List<HistorialInquilinoDto> h = (List<HistorialInquilinoDto>) servicio.historial(LOCAL, "inquilino");

        assertThat(h).extracting(HistorialInquilinoDto::mantenimientoId).containsExactly(1L);
        assertThat(h.get(0).cumplimiento().certificados()).extracting(c -> c.id()).containsExactly(52L);
        // Solo se consultan aprobados de los mantenimientos cubiertos (ni la ocupacion anterior ni P5).
        verify(intervencionRepository).historialDeMantenimientos(
            argThat((Collection<Long> ids) -> ids.size() == 1 && ids.contains(1L)),
            argThat((Collection<EstadoValidacion> e) -> e.size() == 1 && e.contains(EstadoValidacion.APROBADO)));
    }

    @Test
    void p6d3_ut_12_accesoProveedorLocalAjenoYSinOcupacion() {
        assertThatThrownBy(() -> servicio.historial(LOCAL, "proveedor")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.historial(99L, "gestion")).isInstanceOf(RecursoNoDisponibleException.class);
        when(autorizacion.puedeAccederLocal(inquilino, 11L)).thenReturn(false);
        when(localRepository.existsById(11L)).thenReturn(true);
        assertThatThrownBy(() -> servicio.historial(11L, "inquilino")).isInstanceOf(RecursoNoDisponibleException.class);
        when(autorizacion.ocupacionVigente(inquilino, LOCAL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.historial(LOCAL, "inquilino")).isInstanceOf(RecursoNoDisponibleException.class);
        verifyNoInteractions(mantenimientoRepository);
    }

    // ---- Utilidades ----

    private Mantenimiento mantenimiento(Long id, boolean p6, LocalDate fecha, EstadoMantenimiento estado) {
        Mantenimiento m = mock(Mantenimiento.class);
        when(m.getId()).thenReturn(id);
        when(m.getObligacion()).thenReturn(obligacion);
        when(m.isValidacionRequerida()).thenReturn(p6);
        when(m.getFechaProgramada()).thenReturn(fecha);
        when(m.getFechaRealizacion()).thenReturn(fecha);
        when(m.fechaDelHecho()).thenReturn(fecha);
        when(m.getEstado()).thenReturn(estado);
        return m;
    }

    private static Intervencion intervencion(Long id, Mantenimiento m, EstadoValidacion estado, LocalDate fecha) {
        Proveedor p = mock(Proveedor.class);
        when(p.getRazonSocial()).thenReturn("Proveedor sintetico");
        Intervencion i = mock(Intervencion.class);
        when(i.getId()).thenReturn(id);
        when(i.getMantenimiento()).thenReturn(m);
        when(i.getEstadoValidacion()).thenReturn(estado);
        when(i.getFechaEjecucion()).thenReturn(fecha);
        when(i.getResultado()).thenReturn(ResultadoIntervencion.CONFORME);
        when(i.getProveedor()).thenReturn(p);
        return i;
    }

    private static DocumentoMantenimiento documento(Long id, Mantenimiento m, Intervencion i, CategoriaDocumento categoria,
                                                    VisibilidadDocumento visibilidad) {
        DocumentoMantenimiento d = mock(DocumentoMantenimiento.class);
        when(d.getId()).thenReturn(id);
        when(d.getMantenimiento()).thenReturn(m);
        when(d.getIntervencion()).thenReturn(i);
        when(d.getCategoria()).thenReturn(categoria);
        when(d.getVisibilidad()).thenReturn(visibilidad);
        when(d.getEstadoRevision()).thenReturn(EstadoRevisionDocumento.VALIDO);
        when(d.getNombreOriginal()).thenReturn("doc-" + id + ".pdf");
        when(d.getFechaSubida()).thenReturn(LocalDateTime.of(2026, 10, 2, 10, 0));
        return d;
    }
}
