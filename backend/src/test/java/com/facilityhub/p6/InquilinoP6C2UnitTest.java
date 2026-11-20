package com.facilityhub.p6;

import com.facilityhub.api.dto.inquilino.*;
import com.facilityhub.api.dto.proveedor.AsignacionRequest;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FH-006 / P6-C2: pruebas unitarias (Mockito, sin BD) de las funcionalidades del INQUILINO: declaracion y revision de
// proveedor, solicitudes, fecha prevista, asignacion del aceptado, historial por ocupacion y certificados. Sintetico.
class InquilinoP6C2UnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);
    private static final String INQ = "inquilino@sintetico.test";
    private static final String INQ_OTRO = "otro.inquilino@sintetico.test";
    private static final String EXPLOTACION = "explotacion@sintetico.test";
    private static final String ADMIN = "admin@sintetico.test";
    private static final String TECNICO = "tecnico@sintetico.test";
    private static final Long LOCAL = 10L, INQ_ID = 20L, INQ_OTRO_ID = 21L, OCUP = 30L, OCUP_ANTERIOR = 31L;
    private static final Long OBL_I = 40L, OBL_N = 41L, MANT = 50L, MANT_N = 51L, PROV = 60L;

    private UsuarioRepository usuarioRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private ObligacionMantenimientoRepository obligacionRepository;
    private MantenimientoRepository mantenimientoRepository;
    private AsignacionMantenimientoRepository asignacionRepository;
    private IntervencionRepository intervencionRepository;
    private DocumentoMantenimientoRepository documentoRepository;
    private ProveedorRepository proveedorRepository;
    private ObligacionProveedorRepository declaracionRepository;
    private SolicitudProveedorRepository solicitudRepository;
    private AuditoriaService auditoria;
    private DeclaracionProveedorService declaraciones;
    private SolicitudProveedorService solicitudes;
    private AsignacionService asignaciones;
    private InquilinoService inquilinoService;

    private OcupacionLocal ocupacion;
    private ObligacionMantenimiento obligacionI;
    private ObligacionMantenimiento obligacionN;
    private Mantenimiento mantenimiento;
    private Proveedor proveedor;
    private Usuario inquilino;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        obligacionRepository = mock(ObligacionMantenimientoRepository.class);
        mantenimientoRepository = mock(MantenimientoRepository.class);
        asignacionRepository = mock(AsignacionMantenimientoRepository.class);
        intervencionRepository = mock(IntervencionRepository.class);
        documentoRepository = mock(DocumentoMantenimientoRepository.class);
        proveedorRepository = mock(ProveedorRepository.class);
        declaracionRepository = mock(ObligacionProveedorRepository.class);
        solicitudRepository = mock(SolicitudProveedorRepository.class);
        auditoria = mock(AuditoriaService.class);
        DocumentoAutorizacionService autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        PoliticaAccesoService politica = new PoliticaAccesoService(autorizacion, intervencionRepository, asignacionRepository);
        declaraciones = new DeclaracionProveedorService(declaracionRepository, obligacionRepository, ocupacionRepository,
            proveedorRepository, autorizacion, auditoria, RELOJ);
        solicitudes = new SolicitudProveedorService(solicitudRepository, proveedorRepository, ocupacionRepository,
            declaraciones, autorizacion, auditoria, RELOJ);
        asignaciones = new AsignacionService(mantenimientoRepository, asignacionRepository, proveedorRepository,
            usuarioRepository, autorizacion, politica, declaraciones, auditoria, RELOJ);
        inquilinoService = new InquilinoService(ocupacionRepository, obligacionRepository, mantenimientoRepository,
            asignacionRepository, intervencionRepository, documentoRepository, proveedorRepository, declaraciones,
            asignaciones, autorizacion, auditoria, RELOJ);

        Inquilino inq = inquilinoEntidad(INQ_ID);
        inquilino = usuario(INQ, "INQUILINO", inq);
        usuario(INQ_OTRO, "INQUILINO", inquilinoEntidad(INQ_OTRO_ID));
        usuario(EXPLOTACION, "EXPLOTACION", null);
        usuario(ADMIN, "ADMIN", null);
        usuario(TECNICO, "PROVEEDOR", null);

        LocalComercial local = mock(LocalComercial.class);
        when(local.getId()).thenReturn(LOCAL);
        when(local.getCodigo()).thenReturn("L-SINT");
        ocupacion = ocupacion(OCUP, local, inq, LocalDate.of(2026, 6, 1));
        when(ocupacionRepository.ocupacionVigente(LOCAL, INQ_ID, HOY)).thenReturn(Optional.of(ocupacion));
        when(ocupacionRepository.existeOcupacionVigente(LOCAL, INQ_ID, HOY)).thenReturn(true);
        when(ocupacionRepository.ocupacionesVigentesDeInquilino(INQ_ID, HOY)).thenReturn(List.of(ocupacion));
        when(ocupacionRepository.ocupacionesVigentesDelLocal(LOCAL, HOY)).thenReturn(List.of(ocupacion));

        obligacionI = obligacion(OBL_I, local, ResponsableObligacion.INQUILINO);
        obligacionN = obligacion(OBL_N, local, ResponsableObligacion.NHOOD);
        when(obligacionRepository.findByLocalIdAndActivoTrueOrderByProximaFechaAsc(LOCAL)).thenReturn(List.of(obligacionI, obligacionN));

        mantenimiento = spy(new Mantenimiento(obligacionI, LocalDate.of(2026, 12, 15), "Sintetico"));
        doReturn(MANT).when(mantenimiento).getId();
        when(mantenimientoRepository.findById(MANT)).thenReturn(Optional.of(mantenimiento));
        Mantenimiento mantN = spy(new Mantenimiento(obligacionN, LocalDate.of(2026, 12, 20), "Sintetico N"));
        doReturn(MANT_N).when(mantN).getId();
        when(mantenimientoRepository.findById(MANT_N)).thenReturn(Optional.of(mantN));

        proveedor = mock(Proveedor.class);
        when(proveedor.getId()).thenReturn(PROV);
        when(proveedor.isActivo()).thenReturn(true);
        when(proveedor.getRazonSocial()).thenReturn("Proveedor sintetico");
        when(proveedorRepository.findById(PROV)).thenReturn(Optional.of(proveedor));
        when(declaracionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(solicitudRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asignacionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mantenimientoRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- Consultas y acceso ----------

    @Test
    void p6c2_01_obligacionesPropiasConResponsableYAccionesSoloEnLasDelInquilino() {
        List<ObligacionInquilinoDto> lista = inquilinoService.obligaciones(INQ);
        assertThat(lista).extracting(ObligacionInquilinoDto::responsable).containsExactly("INQUILINO", "NHOOD");
        assertThat(lista.get(0).accionesPermitidas()).containsExactly("DECLARAR_PROVEEDOR", "SOLICITAR_PROVEEDOR");
        assertThat(lista.get(1).accionesPermitidas()).isEmpty();
        assertThat(lista.get(1).proveedorDeclarado()).isNull();
        // Solo se consulta la declaracion de SU ocupacion (nunca la de un ocupante anterior).
        verify(declaracionRepository).findFirstByObligacionIdAndOcupacionIdOrderByIdDesc(OBL_I, OCUP);
        assertThat(inquilinoService.obligaciones(INQ_OTRO)).isEmpty();
    }

    @Test
    void p6c2_02_historialProtegidoPorOcupacion() {
        OcupacionLocal anterior = mock(OcupacionLocal.class);
        when(anterior.getId()).thenReturn(OCUP_ANTERIOR);
        Mantenimiento previo = spy(new Mantenimiento(obligacionI, LocalDate.of(2026, 3, 1), "De la ocupacion anterior"));
        Mantenimiento vinculadoOtro = spy(new Mantenimiento(obligacionI, LocalDate.of(2026, 12, 1), "Vinculado a otra"));
        vinculadoOtro.vincularOcupacion(anterior, LocalDateTime.now(RELOJ));
        assertThat(DocumentoAutorizacionService.cubreMantenimiento(ocupacion, previo)).isFalse();
        assertThat(DocumentoAutorizacionService.cubreMantenimiento(ocupacion, vinculadoOtro)).isFalse();
        assertThat(DocumentoAutorizacionService.cubreMantenimiento(ocupacion, mantenimiento)).isTrue();
        when(mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(OBL_I)).thenReturn(List.of(vinculadoOtro, mantenimiento, previo));
        when(obligacionRepository.findById(OBL_I)).thenReturn(Optional.of(obligacionI));
        assertThat(inquilinoService.mantenimientos(OBL_I, INQ)).extracting(MantenimientoInquilinoDetalleDto::id).containsExactly(MANT);
        assertThatThrownBy(() -> inquilinoService.mantenimientos(OBL_I, INQ_OTRO)).isInstanceOf(RecursoNoDisponibleException.class);
    }

    @Test
    void p6c2_03_soloCertificadosAprobadosYCompartidos() {
        when(obligacionRepository.findById(OBL_I)).thenReturn(Optional.of(obligacionI));
        when(mantenimientoRepository.findByObligacionIdOrderByFechaProgramadaDesc(OBL_I)).thenReturn(List.of(mantenimiento));
        Intervencion aprobada = mock(Intervencion.class);
        when(aprobada.getId()).thenReturn(70L);
        when(aprobada.getResultado()).thenReturn(ResultadoIntervencion.CONFORME);
        when(aprobada.getFechaEjecucion()).thenReturn(LocalDate.of(2026, 11, 10));
        when(intervencionRepository.findFirstByMantenimientoIdAndEstadoValidacionIn(MANT, EnumSet.of(EstadoValidacion.APROBADO)))
            .thenReturn(Optional.of(aprobada));
        DocumentoMantenimiento compartido = doc(81L, VisibilidadDocumento.COMPARTIDO, EstadoRevisionDocumento.VALIDO);
        DocumentoMantenimiento interno = doc(82L, VisibilidadDocumento.INTERNO, EstadoRevisionDocumento.VALIDO);
        when(documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(70L)).thenReturn(List.of(compartido, interno));
        MantenimientoInquilinoDetalleDto dto = inquilinoService.mantenimientos(OBL_I, INQ).get(0);
        assertThat(dto.cumplimiento().certificados()).extracting(CertificadoInquilinoDto::id).containsExactly(81L);
        assertThat(dto.accionesPermitidas()).containsExactly("FIJAR_FECHA_PREVISTA"); // sin proveedor aceptado
    }

    // ---------- Declaracion y revision ----------

    @Test
    void p6c2_04_declararProveedorSoloEnObligacionesDelInquilino() {
        when(obligacionRepository.findById(OBL_I)).thenReturn(Optional.of(obligacionI));
        when(obligacionRepository.findById(OBL_N)).thenReturn(Optional.of(obligacionN));
        DeclaracionProveedorDto dto = declaraciones.declarar(OBL_I, PROV, INQ);
        assertThat(dto.estado()).isEqualTo("PROPUESTO");
        verify(auditoria).registrar(eq("OBLIGACION_PROVEEDOR"), any(), eq("DECLARAR_PROVEEDOR"), eq(inquilino), any());
        assertThatThrownBy(() -> declaraciones.declarar(OBL_N, PROV, INQ)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> declaraciones.declarar(OBL_I, PROV, INQ_OTRO)).isInstanceOf(RecursoNoDisponibleException.class);
        when(proveedor.isActivo()).thenReturn(false);
        assertThatThrownBy(() -> declaraciones.declarar(OBL_I, PROV, INQ)).isInstanceOf(RecursoNoDisponibleException.class);
    }

    @Test
    void p6c2_05_redeclararEsIdempotenteYOtroProveedorSustituyeAlAnterior() {
        when(obligacionRepository.findById(OBL_I)).thenReturn(Optional.of(obligacionI));
        ObligacionProveedor actual = new ObligacionProveedor(obligacionI, proveedor, ocupacion, inquilino, LocalDateTime.now(RELOJ));
        when(declaracionRepository.findByObligacionIdAndActivoTrue(OBL_I)).thenReturn(Optional.of(actual));
        declaraciones.declarar(OBL_I, PROV, INQ);
        verify(declaracionRepository, never()).saveAndFlush(any());

        Proveedor otro = mock(Proveedor.class);
        when(otro.getId()).thenReturn(61L);
        when(otro.isActivo()).thenReturn(true);
        when(proveedorRepository.findById(61L)).thenReturn(Optional.of(otro));
        declaraciones.declarar(OBL_I, 61L, INQ);
        assertThat(actual.isActivo()).isFalse();
        verify(declaracionRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void p6c2_06_revisionPorGestionConVersionMotivoYOcupacionVigente() {
        ObligacionProveedor d = new ObligacionProveedor(obligacionI, proveedor, ocupacion, inquilino, LocalDateTime.now(RELOJ));
        when(declaracionRepository.bloquear(5L)).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> declaraciones.revisar(5L, new RevisionDeclaracionRequest(AccionRevisionProveedor.ACEPTAR, null, 0L), INQ))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> declaraciones.revisar(5L, new RevisionDeclaracionRequest(AccionRevisionProveedor.ACEPTAR, null, 9L), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> declaraciones.revisar(5L, new RevisionDeclaracionRequest(AccionRevisionProveedor.RECHAZAR, " ", 0L), EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);
        when(ocupacion.getFechaFin()).thenReturn(HOY.minusDays(1));
        assertThatThrownBy(() -> declaraciones.revisar(5L, new RevisionDeclaracionRequest(AccionRevisionProveedor.ACEPTAR, null, 0L), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);
        when(ocupacion.getFechaFin()).thenReturn(null);
        assertThat(declaraciones.revisar(5L, new RevisionDeclaracionRequest(AccionRevisionProveedor.ACEPTAR, "OK", 0L), EXPLOTACION).estado())
            .isEqualTo("ACEPTADO");
        assertThatThrownBy(() -> declaraciones.revisar(5L, new RevisionDeclaracionRequest(AccionRevisionProveedor.RECHAZAR, "x", 0L), EXPLOTACION))
            .isInstanceOf(ConflictoException.class); // ya no esta pendiente
        verify(auditoria).registrar(eq("OBLIGACION_PROVEEDOR"), any(), eq("ACEPTAR_PROVEEDOR"), any(), any());
    }

    // ---------- Solicitudes ----------

    @Test
    void p6c2_07_solicitudDeProveedorNuevoYResolucionSoloPorAdmin() {
        when(obligacionRepository.findById(OBL_N)).thenReturn(Optional.of(obligacionN));
        assertThatThrownBy(() -> solicitudes.crear(new SolicitudProveedorRequest("Empresa nueva", null, null, null, OBL_N), INQ))
            .isInstanceOf(AccessDeniedException.class);
        SolicitudProveedorDto creada = solicitudes.crear(new SolicitudProveedorRequest("Empresa nueva", "b12345678", "tel", null, null), INQ);
        assertThat(creada.estado()).isEqualTo("PENDIENTE");
        assertThat(creada.cif()).isEqualTo("B12345678");
        assertThatThrownBy(() -> solicitudes.crear(new SolicitudProveedorRequest("x", null, null, null, null), INQ_OTRO))
            .isInstanceOf(RecursoNoDisponibleException.class);

        SolicitudProveedor s = new SolicitudProveedor(inquilino.getInquilino(), null, inquilino, "Empresa nueva", null, null, null, LocalDateTime.now(RELOJ));
        when(solicitudRepository.bloquear(3L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> solicitudes.resolver(3L, new ResolverSolicitudRequest(AccionSolicitudProveedor.ATENDER, PROV, null, 0L), EXPLOTACION))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> solicitudes.resolver(3L, new ResolverSolicitudRequest(AccionSolicitudProveedor.ATENDER, null, null, 0L), ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(solicitudes.resolver(3L, new ResolverSolicitudRequest(AccionSolicitudProveedor.ATENDER, PROV, "Alta hecha", 0L), ADMIN).estado())
            .isEqualTo("ATENDIDA");
        assertThatThrownBy(() -> solicitudes.resolver(3L, new ResolverSolicitudRequest(AccionSolicitudProveedor.RECHAZAR, null, "x", 0L), ADMIN))
            .isInstanceOf(ConflictoException.class);
    }

    // ---------- Fecha prevista y asignacion ----------

    @Test
    void p6c2_08_fechaPrevistaSoloEnLasPropiasConVersionYFechaFutura() {
        when(obligacionRepository.findById(OBL_I)).thenReturn(Optional.of(obligacionI));
        when(obligacionRepository.findById(OBL_N)).thenReturn(Optional.of(obligacionN));
        assertThatThrownBy(() -> inquilinoService.fijarFechaPrevista(MANT_N, new FechaPrevistaRequest(HOY.plusDays(5), 0L), INQ))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> inquilinoService.fijarFechaPrevista(MANT, new FechaPrevistaRequest(HOY.minusDays(1), 0L), INQ))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inquilinoService.fijarFechaPrevista(MANT, new FechaPrevistaRequest(HOY.plusDays(5), 3L), INQ))
            .isInstanceOf(ConflictoException.class);
        MantenimientoInquilinoDetalleDto dto = inquilinoService.fijarFechaPrevista(MANT, new FechaPrevistaRequest(HOY.plusDays(5), 0L), INQ);
        assertThat(dto.fechaPrevistaInquilino()).isEqualTo(HOY.plusDays(5));
        assertThat(mantenimiento.getOcupacion()).isSameAs(ocupacion);
        verify(auditoria).registrar(eq("MANTENIMIENTO"), eq(MANT), eq("FIJAR_FECHA_PREVISTA"), eq(inquilino), any());
        assertThatThrownBy(() -> inquilinoService.fijarFechaPrevista(MANT, new FechaPrevistaRequest(HOY.plusDays(5), 0L), INQ_OTRO))
            .isInstanceOf(RecursoNoDisponibleException.class);
    }

    @Test
    void p6c2_09_asignacionSoloDelProveedorAceptadoDeLaOcupacionVigente() {
        when(obligacionRepository.findById(OBL_I)).thenReturn(Optional.of(obligacionI));
        assertThatThrownBy(() -> inquilinoService.asignarProveedorAceptado(MANT, null, INQ)).isInstanceOf(ConflictoException.class);
        verify(asignacionRepository, never()).saveAndFlush(any());

        ObligacionProveedor aceptada = new ObligacionProveedor(obligacionI, proveedor, ocupacion, inquilino, LocalDateTime.now(RELOJ));
        aceptada.aceptar(mock(Usuario.class), null, LocalDateTime.now(RELOJ));
        when(declaracionRepository.findByObligacionIdAndActivoTrue(OBL_I)).thenReturn(Optional.of(aceptada));
        assertThat(inquilinoService.asignarProveedorAceptado(MANT, null, INQ).proveedorId()).isEqualTo(PROV);
        ArgumentCaptor<AsignacionMantenimiento> a = ArgumentCaptor.forClass(AsignacionMantenimiento.class);
        verify(asignacionRepository).saveAndFlush(a.capture());
        assertThat(a.getValue().getProveedor()).isSameAs(proveedor);
        verify(auditoria).registrar(eq("MANTENIMIENTO"), eq(MANT), eq("ASIGNAR_PROVEEDOR"), eq(inquilino), any());

        // Gestion: ADMIN solo puede asignar ese mismo proveedor aceptado; EXPLOTACION no asigna en obligaciones I.
        Proveedor otro = mock(Proveedor.class);
        when(otro.getId()).thenReturn(61L);
        when(otro.isActivo()).thenReturn(true);
        when(proveedorRepository.findById(61L)).thenReturn(Optional.of(otro));
        assertThatThrownBy(() -> asignaciones.asignar(MANT, new AsignacionRequest(61L, null), ADMIN)).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> asignaciones.asignar(MANT, new AsignacionRequest(PROV, null), EXPLOTACION)).isInstanceOf(AccessDeniedException.class);

        // Si la ocupacion que la declaro ya no es la vigente, la aceptacion no vale para el nuevo ocupante.
        when(ocupacionRepository.ocupacionesVigentesDelLocal(LOCAL, HOY)).thenReturn(List.of());
        assertThatThrownBy(() -> inquilinoService.asignarProveedorAceptado(MANT, null, INQ)).isInstanceOf(ConflictoException.class);
    }

    @Test
    void p6c2_10_tecnicoYEmpresaNoAccedenAlAreaDelInquilino() {
        assertThat(inquilinoService.obligaciones(TECNICO)).isEmpty();
        assertThatThrownBy(() -> inquilinoService.buscarProveedor("B1", TECNICO)).isInstanceOf(RecursoNoDisponibleException.class);
        when(proveedorRepository.findFirstByCifIgnoreCaseAndActivoTrue("B1")).thenReturn(Optional.of(proveedor));
        assertThat(inquilinoService.buscarProveedor(" B1 ", INQ).razonSocial()).isEqualTo("Proveedor sintetico");
        assertThatThrownBy(() -> inquilinoService.buscarProveedor("NOEXISTE", INQ)).isInstanceOf(RecursoNoDisponibleException.class);
    }

    // ---------- Utilidades ----------

    private DocumentoMantenimiento doc(Long id, VisibilidadDocumento v, EstadoRevisionDocumento e) {
        DocumentoMantenimiento d = mock(DocumentoMantenimiento.class);
        when(d.getId()).thenReturn(id);
        when(d.getVisibilidad()).thenReturn(v);
        when(d.getEstadoRevision()).thenReturn(e);
        when(d.getNombreOriginal()).thenReturn("doc.pdf");
        return d;
    }

    private static Inquilino inquilinoEntidad(Long id) {
        Inquilino i = mock(Inquilino.class);
        when(i.getId()).thenReturn(id);
        when(i.isActivo()).thenReturn(true);
        when(i.getRazonSocial()).thenReturn("Inquilino " + id);
        return i;
    }

    private static OcupacionLocal ocupacion(Long id, LocalComercial local, Inquilino inq, LocalDate inicio) {
        OcupacionLocal o = mock(OcupacionLocal.class);
        when(o.getId()).thenReturn(id);
        when(o.getLocal()).thenReturn(local);
        when(o.getInquilino()).thenReturn(inq);
        when(o.getFechaInicio()).thenReturn(inicio);
        when(o.isActivo()).thenReturn(true);
        return o;
    }

    private static ObligacionMantenimiento obligacion(Long id, LocalComercial local, ResponsableObligacion r) {
        ObligacionMantenimiento o = mock(ObligacionMantenimiento.class);
        TipoMantenimiento t = mock(TipoMantenimiento.class);
        when(t.getNombre()).thenReturn("Tipo sintetico");
        when(o.getId()).thenReturn(id);
        when(o.getLocal()).thenReturn(local);
        when(o.getResponsable()).thenReturn(r);
        when(o.isActivo()).thenReturn(true);
        when(o.getTipoMantenimiento()).thenReturn(t);
        return o;
    }

    private Usuario usuario(String email, String rol, Inquilino inq) {
        Usuario u = mock(Usuario.class);
        Rol r = mock(Rol.class);
        when(r.getCodigo()).thenReturn(rol);
        when(u.getRol()).thenReturn(r);
        when(u.getInquilino()).thenReturn(inq);
        when(u.getNombre()).thenReturn("Sintetico");
        when(u.isActivo()).thenReturn(true);
        when(usuarioRepository.findByEmailAndActivoTrue(email)).thenReturn(Optional.of(u));
        return u;
    }
}
