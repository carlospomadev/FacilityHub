package com.facilityhub.p6;

import com.facilityhub.api.dto.proveedor.ActualizarProveedorRequest;
import com.facilityhub.api.dto.proveedor.AsignacionDto;
import com.facilityhub.api.dto.proveedor.AsignacionRequest;
import com.facilityhub.api.dto.proveedor.CrearProveedorRequest;
import com.facilityhub.api.dto.proveedor.CrearTecnicoRequest;
import com.facilityhub.api.dto.proveedor.TrabajoProveedorDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// FH-006 / P6-B: pruebas unitarias (Mockito, sin BD) de proveedores, asignaciones, trabajos del proveedor
// y responsable contractual. Todos los datos son sinteticos.
class ProveedoresAsignacionesP6BUnitTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final String ADMIN = "admin@sintetico.test";
    private static final String EXPLOTACION = "explotacion@sintetico.test";
    private static final String INQUILINO = "inquilino@sintetico.test";
    private static final String TECNICO = "tecnico@sintetico.test";
    private static final String TECNICO_OTRA = "tecnico.otra@sintetico.test";
    private static final Long PROVEEDOR_ID = 50L;
    private static final Long PROVEEDOR_OTRO_ID = 51L;
    private static final Long MANTENIMIENTO_ID = 700L;

    private UsuarioRepository usuarioRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private ProveedorRepository proveedorRepository;
    private RolRepository rolRepository;
    private AsignacionMantenimientoRepository asignacionRepository;
    private MantenimientoRepository mantenimientoRepository;
    private ObligacionMantenimientoRepository obligacionRepository;
    private LocalComercialRepository localRepository;
    private PasswordEncoder passwordEncoder;

    private ProveedorService proveedorService;
    private AsignacionService asignacionService;
    private TrabajoProveedorService trabajoService;
    private ObligacionService obligacionService;
    private MantenimientoService mantenimientoService;
    private DocumentoAutorizacionService autorizacion;

    private Proveedor proveedor;
    private Proveedor proveedorOtro;
    private Usuario tecnico;
    private Rol rolProveedor;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        proveedorRepository = mock(ProveedorRepository.class);
        rolRepository = mock(RolRepository.class);
        asignacionRepository = mock(AsignacionMantenimientoRepository.class);
        mantenimientoRepository = mock(MantenimientoRepository.class);
        obligacionRepository = mock(ObligacionMantenimientoRepository.class);
        localRepository = mock(LocalComercialRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashsintetico");

        autorizacion = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        IntervencionRepository intervencionRepository = mock(IntervencionRepository.class);
        PoliticaAccesoService politica = new PoliticaAccesoService(autorizacion, intervencionRepository, asignacionRepository);
        proveedorService = new ProveedorService(proveedorRepository, usuarioRepository, rolRepository,
            asignacionRepository, autorizacion, passwordEncoder, RELOJ);
        // P6-C2: el servicio de asignacion recibe ademas las declaraciones de proveedor del inquilino y la auditoria.
        DeclaracionProveedorService declaraciones = new DeclaracionProveedorService(mock(ObligacionProveedorRepository.class),
            obligacionRepository, ocupacionRepository, proveedorRepository, autorizacion, mock(AuditoriaService.class), RELOJ);
        asignacionService = new AsignacionService(mantenimientoRepository, asignacionRepository, proveedorRepository,
            usuarioRepository, autorizacion, politica, declaraciones, mock(AuditoriaService.class), RELOJ);
        trabajoService = new TrabajoProveedorService(asignacionRepository, autorizacion, politica, intervencionRepository);
        obligacionService = new ObligacionService(localRepository, obligacionRepository, ocupacionRepository, autorizacion);
        mantenimientoService = new MantenimientoService(mantenimientoRepository, obligacionRepository, autorizacion, intervencionRepository);

        proveedor = proveedor(PROVEEDOR_ID, true);
        proveedorOtro = proveedor(PROVEEDOR_OTRO_ID, true);
        rolProveedor = rol("PROVEEDOR");
        when(rolRepository.findByCodigo("PROVEEDOR")).thenReturn(Optional.of(rolProveedor));

        usuario(ADMIN, "ADMIN", null);
        usuario(EXPLOTACION, "EXPLOTACION", null);
        usuario(INQUILINO, "INQUILINO", null);
        tecnico = usuario(TECNICO, "PROVEEDOR", proveedor);
        when(tecnico.getId()).thenReturn(90L);
        when(tecnico.isActivo()).thenReturn(true);
        Usuario tecnicoOtra = usuario(TECNICO_OTRA, "PROVEEDOR", proveedorOtro);
        when(tecnicoOtra.getId()).thenReturn(91L);
        when(tecnicoOtra.isActivo()).thenReturn(true);
        when(usuarioRepository.findById(90L)).thenReturn(Optional.of(tecnico));
        when(usuarioRepository.findById(91L)).thenReturn(Optional.of(tecnicoOtra));

        when(proveedorRepository.findById(PROVEEDOR_ID)).thenReturn(Optional.of(proveedor));
        when(proveedorRepository.findById(PROVEEDOR_OTRO_ID)).thenReturn(Optional.of(proveedorOtro));
        when(asignacionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asignacionRepository.findByMantenimientoIdAndActivaTrue(any())).thenReturn(Optional.empty());
    }

    // ---------- Proveedores y tecnicos (D-1) ----------

    @Test
    void p6b_01_soloAdminDaDeAltaProveedores() {
        CrearProveedorRequest req = new CrearProveedorRequest("B99999999", "Proveedor Sintetico SL", null, null);
        assertThatThrownBy(() -> proveedorService.crear(req, EXPLOTACION)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> proveedorService.crear(req, INQUILINO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> proveedorService.crear(req, TECNICO)).isInstanceOf(AccessDeniedException.class);
        verify(proveedorRepository, never()).save(any());

        when(proveedorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(proveedorService.crear(req, ADMIN).razonSocial()).isEqualTo("Proveedor Sintetico SL");
        verify(proveedorRepository).save(any());
    }

    @Test
    void p6b_02_gestionConsultaProveedoresYElRestoNo() {
        when(proveedorRepository.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(proveedor));
        assertThat(proveedorService.listar(EXPLOTACION)).hasSize(1);
        assertThat(proveedorService.listar(ADMIN)).hasSize(1);
        assertThatThrownBy(() -> proveedorService.listar(INQUILINO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> proveedorService.listar(TECNICO)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void p6b_03_altaDeTecnicoConHashRolProveedorYEmpresa() {
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo.tecnico@sintetico.test")).thenReturn(false);
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CrearTecnicoRequest req = new CrearTecnicoRequest("Nuevo", "Tecnico", "Nuevo.Tecnico@sintetico.test", "contrasena-sintetica");

        proveedorService.crearTecnico(PROVEEDOR_ID, req, ADMIN);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getRol()).isSameAs(rolProveedor);
        assertThat(guardado.getValue().getProveedor()).isSameAs(proveedor);
        assertThat(guardado.getValue().getInquilino()).isNull();
        assertThat(guardado.getValue().getEmail()).isEqualTo("nuevo.tecnico@sintetico.test");
        assertThat(guardado.getValue().getPasswordHash()).isEqualTo("$2a$10$hashsintetico").doesNotContain("contrasena");
        assertThat(req.toString()).doesNotContain("contrasena-sintetica").doesNotContain("Nuevo.Tecnico");
    }

    @Test
    void p6b_04_altaDeTecnicoRechazadaSiEmailDuplicadoOProveedorDeBaja() {
        CrearTecnicoRequest req = new CrearTecnicoRequest("Dup", null, TECNICO, "contrasena-sintetica");
        when(usuarioRepository.existsByEmailIgnoreCase(TECNICO)).thenReturn(true);
        assertThatThrownBy(() -> proveedorService.crearTecnico(PROVEEDOR_ID, req, ADMIN)).isInstanceOf(ConflictoException.class);

        Proveedor deBaja = proveedor(52L, false);
        when(proveedorRepository.findById(52L)).thenReturn(Optional.of(deBaja));
        CrearTecnicoRequest otro = new CrearTecnicoRequest("Otro", null, "otro@sintetico.test", "contrasena-sintetica");
        assertThatThrownBy(() -> proveedorService.crearTecnico(52L, otro, ADMIN)).isInstanceOf(ConflictoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void p6b_05_bajaDelProveedorCierraSusAsignacionesActivas() {
        Proveedor real = new Proveedor("B11111111", "Baja Sintetica SL", null, null);
        when(proveedorRepository.findById(60L)).thenReturn(Optional.of(real));
        Usuario admin = autorizacion.usuarioActual(ADMIN);
        AsignacionMantenimiento a1 = new AsignacionMantenimiento(mantenimiento(1L, EstadoMantenimiento.PROGRAMADO,
            obligacion(ResponsableObligacion.NHOOD, true)), real, null, admin, LocalDateTime.now(RELOJ));
        AsignacionMantenimiento a2 = new AsignacionMantenimiento(mantenimiento(2L, EstadoMantenimiento.EN_PROCESO,
            obligacion(ResponsableObligacion.NHOOD, true)), real, null, admin, LocalDateTime.now(RELOJ));
        when(asignacionRepository.findByProveedorIdAndActivaTrue(60L)).thenReturn(List.of(a1, a2));

        proveedorService.actualizar(60L, new ActualizarProveedorRequest(null, null, null, false), ADMIN);

        assertThat(real.isActivo()).isFalse();
        assertThat(List.of(a1, a2)).allSatisfy(a -> {
            assertThat(a.isActiva()).isFalse();
            assertThat(a.getMotivoFin()).isEqualTo(MotivoFinAsignacion.PROVEEDOR_BAJA);
            assertThat(a.getFinalizadaAt()).isNotNull();
        });
    }

    // ---------- Asignaciones ----------

    @Test
    void p6b_06_explotacionAsignaEnObligacionNhood() {
        mantenimientoEnRepo(EstadoMantenimiento.PROGRAMADO, ResponsableObligacion.NHOOD);
        AsignacionDto dto = asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(PROVEEDOR_ID, 90L), EXPLOTACION);
        assertThat(dto.activa()).isTrue();
        assertThat(dto.proveedorId()).isEqualTo(PROVEEDOR_ID);
        assertThat(dto.tecnicoUsuarioId()).isEqualTo(90L);
        verify(asignacionRepository).saveAndFlush(any(AsignacionMantenimiento.class));
    }

    @Test
    // P6-C2 (cambio intencionado): en obligaciones INQUILINO, ADMIN ya no asigna cualquier proveedor; solo el ACEPTADO
    // de la ocupacion vigente (cubierto en P6-C2). Sin declaracion aceptada responde 409.
    void p6b_07_explotacionNoAsignaEnObligacionDelInquilinoYAdminSoloElAceptado() {
        mantenimientoEnRepo(EstadoMantenimiento.PROGRAMADO, ResponsableObligacion.INQUILINO);
        assertThatThrownBy(() -> asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(PROVEEDOR_ID, null), EXPLOTACION))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(PROVEEDOR_ID, null), ADMIN))
            .isInstanceOf(ConflictoException.class);
        verify(asignacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void p6b_08_inquilinoYProveedorNoPuedenAsignar() {
        mantenimientoEnRepo(EstadoMantenimiento.PROGRAMADO, ResponsableObligacion.NHOOD);
        AsignacionRequest req = new AsignacionRequest(PROVEEDOR_ID, null);
        assertThatThrownBy(() -> asignacionService.asignar(MANTENIMIENTO_ID, req, INQUILINO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> asignacionService.asignar(MANTENIMIENTO_ID, req, TECNICO)).isInstanceOf(AccessDeniedException.class);
        verify(asignacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void p6b_09_proveedorDeBajaOMantenimientoNoAsignableDevuelvenConflicto() {
        Proveedor deBaja = proveedor(53L, false);
        when(proveedorRepository.findById(53L)).thenReturn(Optional.of(deBaja));
        mantenimientoEnRepo(EstadoMantenimiento.PROGRAMADO, ResponsableObligacion.NHOOD);
        assertThatThrownBy(() -> asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(53L, null), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);

        mantenimientoEnRepo(EstadoMantenimiento.FINALIZADO, ResponsableObligacion.NHOOD);
        assertThatThrownBy(() -> asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(PROVEEDOR_ID, null), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);
        mantenimientoEnRepo(EstadoMantenimiento.CANCELADO, ResponsableObligacion.NHOOD);
        assertThatThrownBy(() -> asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(PROVEEDOR_ID, null), EXPLOTACION))
            .isInstanceOf(ConflictoException.class);
        verify(asignacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void p6b_10_tecnicoDeOtraEmpresaEsRechazado() {
        mantenimientoEnRepo(EstadoMantenimiento.PROGRAMADO, ResponsableObligacion.NHOOD);
        assertThatThrownBy(() -> asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(PROVEEDOR_ID, 91L), EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);
        verify(asignacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void p6b_11_reasignarCierraLaAnteriorYCreaUnaNueva() {
        Mantenimiento m = mantenimientoEnRepo(EstadoMantenimiento.PROGRAMADO, ResponsableObligacion.NHOOD);
        AsignacionMantenimiento anterior = new AsignacionMantenimiento(m, proveedor, tecnico,
            autorizacion.usuarioActual(EXPLOTACION), LocalDateTime.now(RELOJ).minusDays(3));
        when(asignacionRepository.findByMantenimientoIdAndActivaTrue(MANTENIMIENTO_ID)).thenReturn(Optional.of(anterior));

        AsignacionDto nueva = asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(PROVEEDOR_OTRO_ID, null), EXPLOTACION);

        assertThat(anterior.isActiva()).isFalse();
        assertThat(anterior.getMotivoFin()).isEqualTo(MotivoFinAsignacion.REASIGNADA);
        assertThat(nueva.proveedorId()).isEqualTo(PROVEEDOR_OTRO_ID);
        verify(asignacionRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void p6b_12_reasignarAlMismoProveedorYTecnicoEsIdempotente() {
        Mantenimiento m = mantenimientoEnRepo(EstadoMantenimiento.PROGRAMADO, ResponsableObligacion.NHOOD);
        AsignacionMantenimiento actual = new AsignacionMantenimiento(m, proveedor, tecnico,
            autorizacion.usuarioActual(EXPLOTACION), LocalDateTime.now(RELOJ));
        when(asignacionRepository.findByMantenimientoIdAndActivaTrue(MANTENIMIENTO_ID)).thenReturn(Optional.of(actual));

        asignacionService.asignar(MANTENIMIENTO_ID, new AsignacionRequest(PROVEEDOR_ID, 90L), EXPLOTACION);

        assertThat(actual.isActiva()).isTrue();
        verify(asignacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void p6b_13_mantenimientoInexistenteDevuelve404() {
        when(mantenimientoRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> asignacionService.asignar(999L, new AsignacionRequest(PROVEEDOR_ID, null), EXPLOTACION))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- Trabajos del proveedor ----------

    @Test
    void p6b_14_proveedorVeSoloLosTrabajosDeSuEmpresaSinDatosInternos() {
        Mantenimiento m = mantenimiento(MANTENIMIENTO_ID, EstadoMantenimiento.PROGRAMADO, obligacion(ResponsableObligacion.NHOOD, true));
        AsignacionMantenimiento a = new AsignacionMantenimiento(m, proveedor, tecnico, autorizacion.usuarioActual(EXPLOTACION),
            LocalDateTime.now(RELOJ));
        when(asignacionRepository.trabajosActivosDeProveedor(PROVEEDOR_ID)).thenReturn(List.of(a));

        List<TrabajoProveedorDto> trabajos = trabajoService.listar(TECNICO);

        assertThat(trabajos).singleElement().satisfies(t -> {
            assertThat(t.mantenimientoId()).isEqualTo(MANTENIMIENTO_ID);
            assertThat(t.centro()).isEqualTo("Centro Sintetico");
            assertThat(t.local()).isEqualTo("L-S1 · Local Sintetico");
            assertThat(t.accionesPermitidas()).isEmpty();
        });
        verify(asignacionRepository).trabajosActivosDeProveedor(PROVEEDOR_ID);
        verify(asignacionRepository, never()).trabajosActivosDeProveedor(PROVEEDOR_OTRO_ID);
        // El DTO del proveedor no tiene campos de observaciones ni de inquilino.
        assertThat(TrabajoProveedorDto.class.getRecordComponents())
            .extracting(c -> c.getName())
            .doesNotContain("observaciones", "inquilino", "cif", "emailContacto", "telefono", "localId", "obligacionId");
    }

    @Test
    void p6b_15_trabajoNoAsignadoOEmpresaDeBajaDevuelve404OListaVacia() {
        when(asignacionRepository.trabajoActivoDeProveedor(PROVEEDOR_ID, 123L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> trabajoService.obtener(123L, TECNICO)).isInstanceOf(RecursoNoDisponibleException.class);

        when(proveedor.isActivo()).thenReturn(false);
        assertThat(trabajoService.listar(TECNICO)).isEmpty();
        assertThatThrownBy(() -> trabajoService.obtener(MANTENIMIENTO_ID, TECNICO)).isInstanceOf(RecursoNoDisponibleException.class);
        verify(asignacionRepository, never()).trabajosActivosDeProveedor(any());
    }

    @Test
    void p6b_16_otrosRolesNoUsanElEndpointDeTrabajos() {
        assertThatThrownBy(() -> trabajoService.listar(EXPLOTACION)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> trabajoService.listar(INQUILINO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> trabajoService.obtener(MANTENIMIENTO_ID, ADMIN)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void p6b_17_proveedorNoEsGestionNiAccedeALocales() {
        Usuario u = autorizacion.usuarioActual(TECNICO);
        assertThat(autorizacion.esGestion(u)).isFalse();
        assertThat(autorizacion.puedeAccederLocal(u, 1L)).isFalse();
        assertThat(autorizacion.inquilinoConAcceso(u)).isEmpty();
        assertThat(autorizacion.proveedorConAcceso(u)).contains(PROVEEDOR_ID);
    }

    // ---------- Responsable contractual y zonas comunes ----------

    @Test
    void p6b_18_zonaComunNoPuedeSerResponsabilidadDelInquilino() {
        ObligacionMantenimiento o = obligacion(ResponsableObligacion.NHOOD, false);
        when(obligacionRepository.findById(10L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> obligacionService.cambiarResponsable(10L, ResponsableObligacion.INQUILINO, EXPLOTACION))
            .isInstanceOf(IllegalArgumentException.class);
        verify(o, never()).cambiarResponsable(any());
    }

    @Test
    void p6b_19_responsableInquilinoExigeOcupacionVigente() {
        ObligacionMantenimiento o = obligacion(ResponsableObligacion.NHOOD, true);
        when(obligacionRepository.findById(11L)).thenReturn(Optional.of(o));
        when(ocupacionRepository.existeOcupacionVigenteDelLocal(77L, HOY)).thenReturn(false);
        assertThatThrownBy(() -> obligacionService.cambiarResponsable(11L, ResponsableObligacion.INQUILINO, EXPLOTACION))
            .isInstanceOf(ConflictoException.class);

        when(ocupacionRepository.existeOcupacionVigenteDelLocal(77L, HOY)).thenReturn(true);
        obligacionService.cambiarResponsable(11L, ResponsableObligacion.INQUILINO, EXPLOTACION);
        verify(o).cambiarResponsable(ResponsableObligacion.INQUILINO);
    }

    @Test
    void p6b_20_soloGestionCambiaElResponsable() {
        assertThatThrownBy(() -> obligacionService.cambiarResponsable(11L, ResponsableObligacion.NHOOD, INQUILINO))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> obligacionService.cambiarResponsable(11L, ResponsableObligacion.NHOOD, TECNICO))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void p6b_21_entidadRechazaInquilinoEnZonaComun() throws Exception {
        var ctor = ObligacionMantenimiento.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        ObligacionMantenimiento zonaComun = ctor.newInstance();
        assertThatThrownBy(() -> zonaComun.cambiarResponsable(ResponsableObligacion.INQUILINO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> zonaComun.cambiarResponsable(null)).isInstanceOf(IllegalArgumentException.class);
        zonaComun.cambiarResponsable(ResponsableObligacion.NHOOD);
        assertThat(zonaComun.getResponsable()).isEqualTo(ResponsableObligacion.NHOOD);
    }

    @Test
    void p6b_22_mantenimientosDeZonaComunNoSonVisiblesParaElInquilino() {
        ObligacionMantenimiento zonaComun = obligacion(ResponsableObligacion.NHOOD, false);
        when(zonaComun.isActivo()).thenReturn(true);
        when(obligacionRepository.findById(12L)).thenReturn(Optional.of(zonaComun));
        assertThatThrownBy(() -> mantenimientoService.listarPorObligacion(12L, INQUILINO))
            .isInstanceOf(RecursoNoDisponibleException.class);
        Mantenimiento m = mantenimiento(5L, EstadoMantenimiento.PROGRAMADO, zonaComun);
        assertThat(autorizacion.puedeLeerMantenimiento(autorizacion.usuarioActual(INQUILINO), m)).isFalse();
        assertThat(autorizacion.puedeLeerMantenimiento(autorizacion.usuarioActual(EXPLOTACION), m)).isTrue();
    }

    @Test
    void p6b_23_estadosQueAdmitenAsignacion() throws Exception {
        assertThat(mantenimientoReal(EstadoMantenimiento.PROGRAMADO).admiteAsignacion()).isTrue();
        assertThat(mantenimientoReal(EstadoMantenimiento.PENDIENTE).admiteAsignacion()).isTrue();
        assertThat(mantenimientoReal(EstadoMantenimiento.EN_PROCESO).admiteAsignacion()).isTrue();
        assertThat(mantenimientoReal(EstadoMantenimiento.FINALIZADO).admiteAsignacion()).isFalse();
        assertThat(mantenimientoReal(EstadoMantenimiento.CANCELADO).admiteAsignacion()).isFalse();
    }

    // ---------- Utilidades ----------

    private Usuario usuario(String email, String codigoRol, Proveedor prov) {
        Usuario u = mock(Usuario.class);
        Rol r = rol(codigoRol);
        when(u.getRol()).thenReturn(r);
        when(u.getEmail()).thenReturn(email);
        when(u.getNombre()).thenReturn("Sintetico");
        when(u.getProveedor()).thenReturn(prov);
        when(usuarioRepository.findByEmailAndActivoTrue(email)).thenReturn(Optional.of(u));
        return u;
    }

    private static Rol rol(String codigo) {
        Rol r = mock(Rol.class);
        when(r.getCodigo()).thenReturn(codigo);
        return r;
    }

    private static Proveedor proveedor(Long id, boolean activo) {
        Proveedor p = mock(Proveedor.class);
        when(p.getId()).thenReturn(id);
        when(p.isActivo()).thenReturn(activo);
        when(p.getRazonSocial()).thenReturn("Proveedor " + id);
        return p;
    }

    private static ObligacionMantenimiento obligacion(ResponsableObligacion responsable, boolean conLocal) {
        ObligacionMantenimiento o = mock(ObligacionMantenimiento.class);
        when(o.getResponsable()).thenReturn(responsable);
        Centro c = mock(Centro.class);
        when(c.getNombre()).thenReturn("Centro Sintetico");
        when(o.getCentro()).thenReturn(c);
        if (conLocal) {
            LocalComercial l = mock(LocalComercial.class);
            when(l.getId()).thenReturn(77L);
            when(l.getCodigo()).thenReturn("L-S1");
            when(l.getNombre()).thenReturn("Local Sintetico");
            when(o.getLocal()).thenReturn(l);
        }
        TipoMantenimiento t = mock(TipoMantenimiento.class);
        when(t.getNombre()).thenReturn("Tipo Sintetico");
        when(t.getPeriodicidadMeses()).thenReturn(3);
        when(o.getTipoMantenimiento()).thenReturn(t);
        when(o.getEstado()).thenReturn(EstadoObligacion.ACTIVA);
        return o;
    }

    private static Mantenimiento mantenimiento(Long id, EstadoMantenimiento estado, ObligacionMantenimiento o) {
        Mantenimiento m = mock(Mantenimiento.class);
        when(m.getId()).thenReturn(id);
        when(m.getEstado()).thenReturn(estado);
        when(m.getObligacion()).thenReturn(o);
        when(m.getFechaProgramada()).thenReturn(HOY.plusDays(10));
        when(m.admiteAsignacion()).thenReturn(estado == EstadoMantenimiento.PROGRAMADO
            || estado == EstadoMantenimiento.PENDIENTE || estado == EstadoMantenimiento.EN_PROCESO);
        return m;
    }

    private Mantenimiento mantenimientoEnRepo(EstadoMantenimiento estado, ResponsableObligacion responsable) {
        Mantenimiento m = mantenimiento(MANTENIMIENTO_ID, estado, obligacion(responsable, true));
        when(mantenimientoRepository.findById(MANTENIMIENTO_ID)).thenReturn(Optional.of(m));
        return m;
    }

    private static Mantenimiento mantenimientoReal(EstadoMantenimiento estado) throws Exception {
        Mantenimiento m = new Mantenimiento(null, HOY, "sintetico");
        if (estado != EstadoMantenimiento.PROGRAMADO) {
            m.cambiarEstado(estado, estado == EstadoMantenimiento.FINALIZADO ? HOY : null, null);
        }
        return m;
    }
}
