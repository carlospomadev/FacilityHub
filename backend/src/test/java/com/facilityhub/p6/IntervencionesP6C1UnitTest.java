package com.facilityhub.p6;

import com.facilityhub.api.dto.intervencion.DatosIntervencionRequest;
import com.facilityhub.api.dto.intervencion.IntervencionDto;
import com.facilityhub.api.dto.intervencion.ValidacionRequest;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FH-006 / P6-C1: pruebas unitarias (Mockito, sin BD) del ciclo de intervencion: registro, evidencias, envio,
// validacion, correccion y reenvio, NO_REALIZADO, concurrencia y recalculo. Datos sinteticos.
class IntervencionesP6C1UnitTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final String EXPLOTACION = "explotacion@sintetico.test";
    private static final String ADMIN = "admin@sintetico.test";
    private static final String INQUILINO = "inquilino@sintetico.test";
    private static final String TECNICO = "tecnico@sintetico.test";
    private static final String TECNICO_OTRA = "tecnico.otra@sintetico.test";
    private static final Long MANT_ID = 700L;
    private static final Long PROV_ID = 50L;
    private static final Long PROV_OTRA_ID = 51L;

    private UsuarioRepository usuarioRepository;
    private IntervencionRepository intervencionRepository;
    private ValidacionIntervencionRepository validacionRepository;
    private MantenimientoRepository mantenimientoRepository;
    private AsignacionMantenimientoRepository asignacionRepository;
    private DocumentoMantenimientoRepository documentoRepository;
    private DocumentStorageService storage;
    private AuditoriaService auditoria;
    private IntervencionService servicio;

    private Proveedor proveedor;
    private Usuario tecnico;
    private Usuario explotacion;
    private ObligacionMantenimiento obligacion;
    private TipoMantenimiento tipo;
    private Mantenimiento mantenimiento;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        intervencionRepository = mock(IntervencionRepository.class);
        validacionRepository = mock(ValidacionIntervencionRepository.class);
        mantenimientoRepository = mock(MantenimientoRepository.class);
        asignacionRepository = mock(AsignacionMantenimientoRepository.class);
        documentoRepository = mock(DocumentoMantenimientoRepository.class);
        storage = mock(DocumentStorageService.class);
        auditoria = mock(AuditoriaService.class);
        DocumentoAutorizacionService autorizacion =
            new DocumentoAutorizacionService(usuarioRepository, mock(OcupacionLocalRepository.class), RELOJ);
        PoliticaAccesoService politica = new PoliticaAccesoService(autorizacion, intervencionRepository, asignacionRepository);
        servicio = new IntervencionService(intervencionRepository, validacionRepository, mantenimientoRepository,
            asignacionRepository, documentoRepository, storage, autorizacion, politica, auditoria, RELOJ);

        proveedor = proveedor(PROV_ID);
        Proveedor otra = proveedor(PROV_OTRA_ID);
        tecnico = usuario(TECNICO, "PROVEEDOR", proveedor);
        usuario(TECNICO_OTRA, "PROVEEDOR", otra);
        explotacion = usuario(EXPLOTACION, "EXPLOTACION", null);
        usuario(ADMIN, "ADMIN", null);
        usuario(INQUILINO, "INQUILINO", null);

        tipo = mock(TipoMantenimiento.class);
        when(tipo.getPeriodicidadMeses()).thenReturn(3);
        when(tipo.isRequiereDocumento()).thenReturn(true);
        when(tipo.getReglaRecalculo()).thenReturn(ReglaRecalculo.DESDE_EJECUCION);
        when(tipo.getNombre()).thenReturn("Tipo sintetico");
        obligacion = mock(ObligacionMantenimiento.class);
        when(obligacion.getId()).thenReturn(30L);
        when(obligacion.getTipoMantenimiento()).thenReturn(tipo);
        when(obligacion.getFechaInicio()).thenReturn(LocalDate.of(2026, 1, 1));
        when(obligacion.getProximaFecha()).thenReturn(LocalDate.of(2026, 12, 15));
        when(obligacion.getLocal()).thenReturn(mock(LocalComercial.class));
        when(obligacion.getResponsable()).thenReturn(ResponsableObligacion.NHOOD);

        mantenimiento = spy(new Mantenimiento(obligacion, LocalDate.of(2026, 12, 15), "Sintetico"));
        doReturn(MANT_ID).when(mantenimiento).getId();
        when(mantenimientoRepository.findById(MANT_ID)).thenReturn(Optional.of(mantenimiento));
        when(mantenimientoRepository.existsById(MANT_ID)).thenReturn(true);

        AsignacionMantenimiento asignacion = new AsignacionMantenimiento(mantenimiento, proveedor, tecnico, explotacion,
            LocalDateTime.now(RELOJ));
        when(asignacionRepository.trabajoActivoDeProveedor(PROV_ID, MANT_ID)).thenReturn(Optional.of(asignacion));
        when(asignacionRepository.trabajoActivoDeProveedor(eq(PROV_OTRA_ID), anyLong())).thenReturn(Optional.empty());
        when(asignacionRepository.findByMantenimientoIdAndActivaTrue(MANT_ID)).thenReturn(Optional.of(asignacion));
        when(intervencionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(intervencionRepository.ultimaEjecucionAprobada(anyLong())).thenReturn(null);
    }

    // ---------- Registro ----------

    @Test
    void p6c1_01_proveedorRegistraBorradorEnSuTrabajoYPasaAEnProceso() {
        IntervencionDto dto = servicio.registrar(MANT_ID, datos(HOY.minusDays(1), ResultadoIntervencion.CONFORME), TECNICO);
        assertThat(dto.origen()).isEqualTo("PROVEEDOR");
        assertThat(dto.estadoValidacion()).isEqualTo("BORRADOR");
        assertThat(dto.accionesPermitidas()).containsExactly("EDITAR_INTERVENCION", "ADJUNTAR_DOCUMENTO", "ENVIAR_INTERVENCION");
        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.EN_PROCESO);
        verify(auditoria).registrar(eq("INTERVENCION"), any(), eq("REGISTRAR"), eq(tecnico), any());
    }

    @Test
    void p6c1_02_proveedorDeOtraEmpresaEInquilinoNoRegistran() {
        assertThatThrownBy(() -> servicio.registrar(MANT_ID, null, TECNICO_OTRA)).isInstanceOf(RecursoNoDisponibleException.class);
        assertThatThrownBy(() -> servicio.registrar(MANT_ID, null, INQUILINO)).isInstanceOf(AccessDeniedException.class);
        verify(intervencionRepository, never()).saveAndFlush(any());
    }

    @Test
    void p6c1_03_mantenimientoHistoricoP5OConIntervencionAbiertaNoAdmiteRegistro() {
        when(intervencionRepository.existsByMantenimientoIdAndEstadoValidacionIn(eq(MANT_ID), any())).thenReturn(true);
        assertThatThrownBy(() -> servicio.registrar(MANT_ID, null, TECNICO)).isInstanceOf(ConflictoException.class);
        when(intervencionRepository.existsByMantenimientoIdAndEstadoValidacionIn(eq(MANT_ID), any())).thenReturn(false);
        doReturn(false).when(mantenimiento).isValidacionRequerida();
        assertThatThrownBy(() -> servicio.registrar(MANT_ID, null, TECNICO)).isInstanceOf(ConflictoException.class);
        verify(intervencionRepository, never()).saveAndFlush(any());
    }

    // ---------- Envio ----------

    @Test
    void p6c1_04_envioExigeEvidenciaYNoCambiaLaProximaFecha() {
        Intervencion i = enBorrador(HOY.minusDays(1), ResultadoIntervencion.CONFORME, null);
        when(documentoRepository.countByIntervencionId(any())).thenReturn(0L);
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(IllegalArgumentException.class);

        when(documentoRepository.countByIntervencionId(any())).thenReturn(1L);
        IntervencionDto dto = servicio.enviar(9L, 0L, TECNICO);

        assertThat(dto.estadoValidacion()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(dto.numeroEnvio()).isEqualTo(1);
        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.FINALIZADO);
        assertThat(mantenimiento.getFechaRealizacion()).isEqualTo(HOY.minusDays(1));
        assertThat(i.isEjecucionAnticipada()).isTrue(); // ejecutada antes de la fecha programada: se admite y se marca
        verify(obligacion, never()).actualizarProximaFecha(any());
        verify(auditoria).registrar(eq("INTERVENCION"), any(), eq("ENVIAR"), eq(tecnico), any());
    }

    @Test
    void p6c1_05_envioRechazaFechaFuturaOAnteriorALaUltimaAprobada() {
        enBorrador(HOY.plusDays(1), ResultadoIntervencion.CONFORME, null);
        when(documentoRepository.countByIntervencionId(any())).thenReturn(1L);
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(IllegalArgumentException.class);

        enBorrador(LocalDate.of(2026, 8, 1), ResultadoIntervencion.CONFORME, null);
        when(intervencionRepository.ultimaEjecucionAprobada(30L)).thenReturn(LocalDate.of(2026, 9, 1));
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void p6c1_06_dobleEnvioYVersionDesfasadaDevuelven409() {
        enBorrador(HOY.minusDays(1), ResultadoIntervencion.CONFORME, null);
        when(documentoRepository.countByIntervencionId(any())).thenReturn(1L);
        assertThatThrownBy(() -> servicio.enviar(9L, 5L, TECNICO)).isInstanceOf(ConflictoException.class);
        servicio.enviar(9L, 0L, TECNICO);
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(ConflictoException.class);
    }

    @Test
    void p6c1_07_incidenciasObligatoriasYNoRealizadoNoFinaliza() {
        enBorrador(HOY.minusDays(1), ResultadoIntervencion.CON_INCIDENCIAS, null);
        when(documentoRepository.countByIntervencionId(any())).thenReturn(1L);
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(IllegalArgumentException.class);

        Intervencion nr = enBorrador(HOY.minusDays(1), ResultadoIntervencion.NO_REALIZADO, "Acceso denegado al local");
        when(documentoRepository.countByIntervencionId(any())).thenReturn(0L); // sin documento: no se exige
        servicio.enviar(9L, 0L, TECNICO);
        assertThat(nr.getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE_VALIDACION);
        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.PROGRAMADO);
        assertThat(mantenimiento.getFechaRealizacion()).isNull();
        verify(obligacion, never()).actualizarProximaFecha(any());
    }

    // ---------- Validacion ----------

    @Test
    void p6c1_08_aprobarRecalculaDesdeEjecucionYCompartirEsExplicito() {
        Intervencion i = enviada(HOY.minusDays(10), ResultadoIntervencion.CONFORME);
        DocumentoMantenimiento certificado = documento(81L, i, CategoriaDocumento.CERTIFICADO);
        DocumentoMantenimiento evidencia = documento(82L, i, CategoriaDocumento.EVIDENCIA);
        when(documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(any())).thenReturn(List.of(certificado, evidencia));

        IntervencionDto dto = servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, i.getVersion(), List.of(81L)), EXPLOTACION);

        assertThat(dto.estadoValidacion()).isEqualTo("APROBADO");
        verify(obligacion).actualizarProximaFecha(HOY.minusDays(10).plusMonths(3));
        assertThat(certificado.getEstadoRevision()).isEqualTo(EstadoRevisionDocumento.VALIDO);
        assertThat(certificado.getVisibilidad()).isEqualTo(VisibilidadDocumento.COMPARTIDO);
        assertThat(evidencia.getEstadoRevision()).isEqualTo(EstadoRevisionDocumento.VALIDO);
        assertThat(evidencia.getVisibilidad()).isEqualTo(VisibilidadDocumento.INTERNO); // no se comparte sin decision explicita
        ArgumentCaptor<ValidacionIntervencion> v = ArgumentCaptor.forClass(ValidacionIntervencion.class);
        verify(validacionRepository).save(v.capture());
        assertThat(v.getValue().getAccion()).isEqualTo(AccionValidacion.APROBAR);
        assertThat(v.getValue().getUsuario()).isSameAs(explotacion);
        verify(auditoria).registrar(eq("INTERVENCION"), any(), eq("APROBAR"), eq(explotacion), any());
    }

    @Test
    void p6c1_09_reglaDesdeProgramadaUsaLaFechaProgramada() {
        when(tipo.getReglaRecalculo()).thenReturn(ReglaRecalculo.DESDE_PROGRAMADA);
        Intervencion i = enviada(HOY.minusDays(10), ResultadoIntervencion.CONFORME);
        servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, i.getVersion(), null), EXPLOTACION);
        verify(obligacion).actualizarProximaFecha(LocalDate.of(2026, 12, 15).plusMonths(3));
    }

    @Test
    void p6c1_10_noSeCompartenEvidenciasNiDocumentosAjenosNiEnZonaComun() {
        Intervencion i = enviada(HOY.minusDays(10), ResultadoIntervencion.CONFORME);
        DocumentoMantenimiento evidencia = documento(82L, i, CategoriaDocumento.EVIDENCIA);
        when(documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(any())).thenReturn(List.of(evidencia));
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, 0L, List.of(82L)), EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, 0L, List.of(999L)), EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);

        Intervencion zc = enviada(HOY.minusDays(10), ResultadoIntervencion.CONFORME);
        when(obligacion.getLocal()).thenReturn(null);
        DocumentoMantenimiento cert = documento(83L, zc, CategoriaDocumento.CERTIFICADO);
        when(documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(any())).thenReturn(List.of(cert));
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, 0L, List.of(83L)), EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void p6c1_11_noRealizadoNoSeApruebaYSoloAdmiteCorreccion() {
        Intervencion i = enviada(HOY.minusDays(1), ResultadoIntervencion.NO_REALIZADO);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, 0L, null), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);
        assertThat(i.getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE_VALIDACION);
        verify(obligacion, never()).actualizarProximaFecha(any());
        assertThatThrownBy(() -> i.aprobar(LocalDateTime.now(RELOJ))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void p6c1_12_correccionConComentarioReabreYPermiteReenvio() {
        Intervencion i = enviada(HOY.minusDays(3), ResultadoIntervencion.CONFORME);
        mantenimiento.registrarEjecucion(HOY.minusDays(3), LocalDateTime.now(RELOJ));
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.SOLICITAR_CORRECCION, " ", 0L, null), EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);

        servicio.validar(9L, new ValidacionRequest(AccionValidacion.SOLICITAR_CORRECCION, "Falta el certificado firmado", 0L, null), EXPLOTACION);
        assertThat(i.getEstadoValidacion()).isEqualTo(EstadoValidacion.REQUIERE_CORRECCION);
        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.EN_PROCESO);
        assertThat(mantenimiento.getFechaRealizacion()).isNull();
        verify(obligacion, never()).actualizarProximaFecha(any());

        when(documentoRepository.countByIntervencionId(any())).thenReturn(1L);
        servicio.enviar(9L, i.getVersion(), TECNICO);
        assertThat(i.getNumeroEnvio()).isEqualTo(2);
        assertThat(i.getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE_VALIDACION);
    }

    @Test
    void p6c1_13_soloGestionValidaYNuncaUnaIntervencionNoPresentada() {
        Intervencion i = enviada(HOY.minusDays(1), ResultadoIntervencion.CONFORME);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, 0L, null), TECNICO))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, 0L, null), INQUILINO))
            .isInstanceOf(AccessDeniedException.class);
        enBorrador(HOY.minusDays(1), ResultadoIntervencion.CONFORME, null);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, null, 0L, null), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);
        verify(obligacion, never()).actualizarProximaFecha(any());
        assertThat(i.getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE_VALIDACION);
    }

    @Test
    void p6c1_14_registroExternoDeGestionYAprobacionSeparadaPorElMismoUsuario() {
        IntervencionDto registro = servicio.registrar(MANT_ID, datos(HOY.minusDays(2), ResultadoIntervencion.CONFORME), EXPLOTACION);
        assertThat(registro.origen()).isEqualTo("REGISTRO_GESTION");
        assertThat(registro.estadoValidacion()).isEqualTo("BORRADOR"); // sin aprobacion implicita
        ArgumentCaptor<Intervencion> creada = ArgumentCaptor.forClass(Intervencion.class);
        verify(intervencionRepository).saveAndFlush(creada.capture());
        Intervencion i = creada.getValue();
        // El tecnico del proveedor no puede editar un registro de gestion.
        when(intervencionRepository.findById(9L)).thenReturn(Optional.of(i));
        when(intervencionRepository.bloquear(9L)).thenReturn(Optional.of(i));
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(AccessDeniedException.class);

        when(documentoRepository.countByIntervencionId(any())).thenReturn(1L);
        servicio.enviar(9L, 0L, EXPLOTACION);
        servicio.validar(9L, new ValidacionRequest(AccionValidacion.APROBAR, "Revisado", i.getVersion(), null), EXPLOTACION);
        assertThat(i.getEstadoValidacion()).isEqualTo(EstadoValidacion.APROBADO);
        verify(auditoria).registrar(eq("INTERVENCION"), any(), eq("REGISTRAR"), eq(explotacion), any());
        verify(auditoria).registrar(eq("INTERVENCION"), any(), eq("ENVIAR"), eq(explotacion), any());
        verify(auditoria).registrar(eq("INTERVENCION"), any(), eq("APROBAR"), eq(explotacion), any());
    }

    @Test
    void p6c1_15_proveedorDeOtraEmpresaNoLeeNiEditaIntervencionesAjenas() {
        enBorrador(HOY.minusDays(1), ResultadoIntervencion.CONFORME, null);
        assertThatThrownBy(() -> servicio.obtener(9L, TECNICO_OTRA)).isInstanceOf(RecursoNoDisponibleException.class);
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO_OTRA)).isInstanceOf(RecursoNoDisponibleException.class);
        assertThatThrownBy(() -> servicio.obtener(9L, INQUILINO)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void p6c1_16_reglasDeRecalculoYDetalleDeAuditoria() {
        LocalDate ejec = LocalDate.of(2026, 11, 10), prog = LocalDate.of(2026, 12, 15);
        assertThat(ReglaRecalculo.DESDE_EJECUCION.siguiente(ejec, prog, 6)).isEqualTo(LocalDate.of(2027, 5, 10));
        assertThat(ReglaRecalculo.DESDE_PROGRAMADA.siguiente(ejec, prog, 6)).isEqualTo(LocalDate.of(2027, 6, 15));
        Map<String, Object> d = AuditoriaService.detalle("numeroEnvio", 2, "resultado", "CONFORME", "anticipada", true);
        assertThat(d).containsEntry("numeroEnvio", 2).containsEntry("anticipada", true);
    }

    // ---------- Ajuste NO_REALIZADO: cierre sin cumplimiento y fechas ----------

    @Test
    void p6c1_17_cierreSinCumplimientoConservaEvidenciasInternasYNoCumple() {
        Intervencion i = enviada(LocalDate.of(2026, 11, 10), ResultadoIntervencion.NO_REALIZADO);
        DocumentoMantenimiento foto = documento(91L, i, CategoriaDocumento.EVIDENCIA);
        when(documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(any())).thenReturn(List.of(foto));
        IntervencionDto pendiente = servicio.obtener(9L, EXPLOTACION);
        assertThat(pendiente.accionesPermitidas()).containsExactly("SOLICITAR_CORRECCION", "CERRAR_SIN_CUMPLIMIENTO");

        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO, " ", 0L, null), EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO, "x", 0L, List.of(91L)), EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(i.getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE_VALIDACION);

        IntervencionDto dto = servicio.validar(9L, new ValidacionRequest(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO,
            "Local cerrado; se reprograma", 0L, null), EXPLOTACION);
        assertThat(dto.estadoValidacion()).isEqualTo("CERRADO_SIN_CUMPLIMIENTO");
        assertThat(dto.accionesPermitidas()).isEmpty();
        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.PROGRAMADO);
        assertThat(mantenimiento.getFechaRealizacion()).isNull();
        assertThat(foto.getEstadoRevision()).isEqualTo(EstadoRevisionDocumento.VALIDO);
        assertThat(foto.getVisibilidad()).isEqualTo(VisibilidadDocumento.INTERNO);
        verify(obligacion, never()).actualizarProximaFecha(any());
        ArgumentCaptor<ValidacionIntervencion> v = ArgumentCaptor.forClass(ValidacionIntervencion.class);
        verify(validacionRepository).save(v.capture());
        assertThat(v.getValue().getAccion()).isEqualTo(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO);
        verify(auditoria).registrar(eq("INTERVENCION"), any(), eq("CERRAR_SIN_CUMPLIMIENTO"), eq(explotacion), any());
        // Estado final: no se vuelve a validar ni a editar.
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO, "x", 0L, null), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(ConflictoException.class);
    }

    @Test
    void p6c1_18_cierreSoloParaNoRealizadoYSoloPorGestion() {
        enviada(HOY.minusDays(1), ResultadoIntervencion.CONFORME);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO, "x", 0L, null), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);
        Intervencion nr = enviada(HOY.minusDays(1), ResultadoIntervencion.NO_REALIZADO);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO, "x", 0L, null), TECNICO))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.validar(9L, new ValidacionRequest(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO, "x", 0L, null), INQUILINO))
            .isInstanceOf(AccessDeniedException.class);
        servicio.validar(9L, new ValidacionRequest(AccionValidacion.CERRAR_SIN_CUMPLIMIENTO, "Cierre por ADMIN", 0L, null), ADMIN);
        assertThat(nr.getEstadoValidacion()).isEqualTo(EstadoValidacion.CERRADO_SIN_CUMPLIMIENTO);
        assertThatThrownBy(() -> enviada(HOY.minusDays(1), ResultadoIntervencion.CONFORME).cerrarSinCumplimiento(LocalDateTime.now(RELOJ)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void p6c1_19_noRealizadoHistoricoAdmitidoSinFechasFuturasNiAnterioresAlInicio() {
        when(intervencionRepository.ultimaEjecucionAprobada(30L)).thenReturn(LocalDate.of(2026, 10, 1));
        // Intento historico anterior a la ultima ejecucion aprobada: se admite (no es cumplimiento).
        Intervencion h = enBorrador(LocalDate.of(2026, 9, 15), ResultadoIntervencion.NO_REALIZADO, "Sin acceso");
        servicio.enviar(9L, 0L, TECNICO);
        assertThat(h.getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE_VALIDACION);
        verify(obligacion, never()).actualizarProximaFecha(any());
        // La misma fecha con resultado ejecutado si respeta la cronologia de cumplimiento: se rechaza.
        enBorrador(LocalDate.of(2026, 9, 15), ResultadoIntervencion.CONFORME, null);
        when(documentoRepository.countByIntervencionId(any())).thenReturn(1L);
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(IllegalArgumentException.class);
        enBorrador(HOY.plusDays(1), ResultadoIntervencion.NO_REALIZADO, "Futuro");
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(IllegalArgumentException.class);
        enBorrador(LocalDate.of(2025, 12, 31), ResultadoIntervencion.NO_REALIZADO, "Antes del inicio");
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void p6c1_20_unIntentoNoAlteraUnCumplimientoYaAprobado() {
        mantenimiento.registrarEjecucion(LocalDate.of(2026, 11, 1), LocalDateTime.now(RELOJ));
        when(intervencionRepository.existsByMantenimientoIdAndEstadoValidacionIn(MANT_ID, List.of(EstadoValidacion.APROBADO))).thenReturn(true);
        enBorrador(LocalDate.of(2026, 10, 20), ResultadoIntervencion.NO_REALIZADO, "Visita historica");
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(ConflictoException.class);
        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.FINALIZADO);
        assertThat(mantenimiento.getFechaRealizacion()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void p6c1_21_intentoEnviadoQuedaProgramadoYSuCorreccionLoReabreEnCurso() {
        mantenimiento.iniciarEjecucion(LocalDateTime.now(RELOJ));
        Intervencion nr = enBorrador(HOY.minusDays(1), ResultadoIntervencion.NO_REALIZADO, "Sin acceso");
        servicio.enviar(9L, 0L, TECNICO);
        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.PROGRAMADO);
        assertThatThrownBy(() -> servicio.enviar(9L, 0L, TECNICO)).isInstanceOf(ConflictoException.class); // doble envio
        assertThat(nr.getNumeroEnvio()).isEqualTo(1);
        servicio.validar(9L, new ValidacionRequest(AccionValidacion.SOLICITAR_CORRECCION, "Adjunta la foto del cierre", 0L, null), EXPLOTACION);
        assertThat(nr.getEstadoValidacion()).isEqualTo(EstadoValidacion.REQUIERE_CORRECCION);
        assertThat(mantenimiento.getEstado()).isEqualTo(EstadoMantenimiento.EN_PROCESO);
        assertThat(mantenimiento.getFechaRealizacion()).isNull();
    }

    // ---------- Utilidades ----------

    private DatosIntervencionRequest datos(LocalDate fecha, ResultadoIntervencion r) {
        return new DatosIntervencionRequest(null, fecha, "Actuaciones sinteticas", r, null);
    }

    private Intervencion enBorrador(LocalDate fecha, ResultadoIntervencion r, String incidencias) {
        Intervencion i = new Intervencion(mantenimiento, proveedor, tecnico, OrigenIntervencion.PROVEEDOR, tecnico, LocalDateTime.now(RELOJ));
        i.actualizar(fecha, "Actuaciones sinteticas", r, incidencias, LocalDateTime.now(RELOJ));
        when(intervencionRepository.findById(9L)).thenReturn(Optional.of(i));
        when(intervencionRepository.bloquear(9L)).thenReturn(Optional.of(i));
        return i;
    }

    private Intervencion enviada(LocalDate fecha, ResultadoIntervencion r) {
        Intervencion i = enBorrador(fecha, r, r == ResultadoIntervencion.CONFORME ? null : "Incidencia sintetica");
        i.enviar(tecnico, LocalDateTime.now(RELOJ), mantenimiento.getFechaProgramada());
        return i;
    }

    private DocumentoMantenimiento documento(Long id, Intervencion i, CategoriaDocumento c) {
        DocumentoMantenimiento d = spy(DocumentoMantenimiento.deIntervencion(i, "sintetico.pdf", "x", c, tecnico));
        doReturn(id).when(d).getId();
        return d;
    }

    private Usuario usuario(String email, String rol, Proveedor p) {
        Usuario u = mock(Usuario.class);
        Rol r = mock(Rol.class);
        when(r.getCodigo()).thenReturn(rol);
        when(u.getRol()).thenReturn(r);
        when(u.getProveedor()).thenReturn(p);
        when(u.getNombre()).thenReturn("Sintetico");
        when(u.isActivo()).thenReturn(true);
        when(usuarioRepository.findByEmailAndActivoTrue(email)).thenReturn(Optional.of(u));
        return u;
    }

    private static Proveedor proveedor(Long id) {
        Proveedor p = mock(Proveedor.class);
        when(p.getId()).thenReturn(id);
        when(p.isActivo()).thenReturn(true);
        when(p.getRazonSocial()).thenReturn("Proveedor " + id);
        return p;
    }
}
