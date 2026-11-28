package com.facilityhub.fh010;

import com.facilityhub.api.dto.CrearInstalacionRequest;
import com.facilityhub.api.dto.InstalacionCentroDto;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import com.facilityhub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// FH-010A / RF-07: reglas del alta de instalaciones (Mockito, sin BD). Todos los datos son sinteticos.
class InstalacionesFH010AUnitTest {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final Long CENTRO_ID = 10L;
    private static final Long OTRO_CENTRO_ID = 11L;
    private static final Long LOCAL_ID = 100L;

    private UsuarioRepository usuarioRepository;
    private CentroRepository centroRepository;
    private LocalComercialRepository localRepository;
    private InstalacionRepository instalacionRepository;
    private InstalacionService servicio;
    private Centro centro;
    private LocalComercial local;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        centroRepository = mock(CentroRepository.class);
        localRepository = mock(LocalComercialRepository.class);
        // FH-014 (D7): el estado del local se lee tras bloquear su fila; en estas pruebas, el del local simulado.
        when(localRepository.bloquearYEstaActivo(any())).thenAnswer(inv -> localRepository.findById(inv.getArgument(0))
            .map(LocalComercial::isActivo).orElse(false));
        instalacionRepository = mock(InstalacionRepository.class);
        DocumentoAutorizacionService autorizacion =
            new DocumentoAutorizacionService(usuarioRepository, mock(OcupacionLocalRepository.class), RELOJ);
        servicio = new InstalacionService(centroRepository, localRepository, instalacionRepository, autorizacion, RELOJ);
        usuario("admin@sintetico.test", "ADMIN");
        usuario("explotacion@sintetico.test", "EXPLOTACION");
        usuario("inquilino@sintetico.test", "INQUILINO");
        usuario("proveedor@sintetico.test", "PROVEEDOR");
        centro = centro(CENTRO_ID, true);
        local = local(LOCAL_ID, centro, true);
        when(centroRepository.findParaAlta(CENTRO_ID)).thenReturn(Optional.of(centro));
        when(localRepository.findById(LOCAL_ID)).thenReturn(Optional.of(local));
        when(instalacionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void altaEnLocalNormalizaYGuardaActivaPorDefecto() {
        InstalacionCentroDto dto = servicio.crear(CENTRO_ID,
            new CrearInstalacionRequest(LOCAL_ID, "  Grupo electrogeno  ", " ELECTRICIDAD ", "  ", null), "admin@sintetico.test");

        ArgumentCaptor<Instalacion> guardada = ArgumentCaptor.forClass(Instalacion.class);
        verify(instalacionRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getNombre()).isEqualTo("Grupo electrogeno");
        assertThat(guardada.getValue().getTipo()).isEqualTo("ELECTRICIDAD");
        assertThat(guardada.getValue().getDescripcion()).isNull();
        assertThat(guardada.getValue().getEstado()).isEqualTo(EstadoInstalacion.ACTIVA);
        assertThat(dto.centroId()).isEqualTo(CENTRO_ID);
        assertThat(dto.localId()).isEqualTo(LOCAL_ID);
    }

    @Test
    void altaDeZonaComunSinLocalYConEstadoInformado() {
        InstalacionCentroDto dto = servicio.crear(CENTRO_ID,
            new CrearInstalacionRequest(null, "Ascensor norte", "ELEVACION", null, "FUERA_SERVICIO"), "explotacion@sintetico.test");

        assertThat(dto.localId()).isNull();
        assertThat(dto.estado()).isEqualTo("FUERA_SERVICIO");
        verify(instalacionRepository).existsByCentroIdAndLocalIsNullAndNombreIgnoreCase(CENTRO_ID, "Ascensor norte");
        verify(localRepository, never()).findById(any());
    }

    @Test
    void proveedorEInquilinoNoPuedenDarDeAltaNiConsultarElCentro() {
        for (String email : new String[] {"inquilino@sintetico.test", "proveedor@sintetico.test"}) {
            assertThatThrownBy(() -> servicio.crear(CENTRO_ID, peticion(LOCAL_ID), email))
                .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.listarDelCentro(CENTRO_ID, email))
                .isInstanceOf(AccessDeniedException.class);
        }
        verify(centroRepository, never()).findParaAlta(any());
        verify(instalacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void centroInexistenteOInactivo() {
        assertThatThrownBy(() -> servicio.crear(999L, peticion(null), "admin@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);
        Centro inactivo = centro(12L, false);
        when(centroRepository.findParaAlta(12L)).thenReturn(Optional.of(inactivo));
        assertThatThrownBy(() -> servicio.crear(12L, peticion(null), "admin@sintetico.test"))
            .isInstanceOf(ConflictoException.class);
        verify(instalacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void localInexistenteDeOtroCentroOInactivo() {
        assertThatThrownBy(() -> servicio.crear(CENTRO_ID, peticion(999L), "admin@sintetico.test"))
            .isInstanceOf(RecursoNoEncontradoException.class);

        LocalComercial ajeno = local(101L, centro(OTRO_CENTRO_ID, true), true);
        when(localRepository.findById(101L)).thenReturn(Optional.of(ajeno));
        assertThatThrownBy(() -> servicio.crear(CENTRO_ID, peticion(101L), "admin@sintetico.test"))
            .isInstanceOf(IllegalArgumentException.class);

        LocalComercial inactivo = local(102L, centro, false);
        when(localRepository.findById(102L)).thenReturn(Optional.of(inactivo));
        assertThatThrownBy(() -> servicio.crear(CENTRO_ID, peticion(102L), "admin@sintetico.test"))
            .isInstanceOf(ConflictoException.class);
        verify(instalacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicadoEnLaMismaUbicacionDa409() {
        when(instalacionRepository.existsByLocalIdAndNombreIgnoreCase(LOCAL_ID, "Climatizacion")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(CENTRO_ID,
            new CrearInstalacionRequest(LOCAL_ID, " Climatizacion ", "CLIMA", null, null), "admin@sintetico.test"))
            .isInstanceOf(ConflictoException.class);
        verify(instalacionRepository, never()).saveAndFlush(any());
    }

    private static CrearInstalacionRequest peticion(Long localId) {
        return new CrearInstalacionRequest(localId, "Instalacion sintetica", "PCI", null, null);
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
        when(l.getCodigo()).thenReturn("L-SINT");
        return l;
    }
}
