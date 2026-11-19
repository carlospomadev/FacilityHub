package com.facilityhub.documentos;

import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoRevisionDocumento;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.domain.VisibilidadDocumento;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.UsuarioRepository;
import com.facilityhub.service.DocumentoAutorizacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static com.facilityhub.documentos.DocumentosFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentoAutorizacionServiceTest {
    private UsuarioRepository usuarioRepository;
    private OcupacionLocalRepository ocupacionRepository;
    private DocumentoAutorizacionService servicio;

    private final Mantenimiento propio = mantenimiento(MANTENIMIENTO_PROPIO, LOCAL_PROPIO);
    private final Mantenimiento ajeno = mantenimiento(MANTENIMIENTO_AJENO, LOCAL_AJENO);

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        ocupacionRepository = mock(OcupacionLocalRepository.class);
        servicio = new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY)).thenReturn(true);
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_AJENO, INQUILINO_ID, HOY)).thenReturn(false);
    }

    @Test
    void usuarioInactivoOInexistenteNoPuedeOperar() {
        when(usuarioRepository.findByEmailAndActivoTrue("baja@sintetico.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.usuarioActual("baja@sintetico.test"))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.usuarioActual(null))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void elRolSeTomaDeLaBdYNoDelToken() {
        // El token pudo emitirse como EXPLOTACION, pero en BD el usuario ya es INQUILINO.
        Usuario degradado = usuario("cambio@sintetico.test", "INQUILINO", null);
        when(usuarioRepository.findByEmailAndActivoTrue("cambio@sintetico.test")).thenReturn(Optional.of(degradado));

        Usuario actual = servicio.usuarioActual("cambio@sintetico.test");

        assertThat(servicio.esGestion(actual)).isFalse();
        assertThatThrownBy(() -> servicio.exigirGestion(actual)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminYExplotacionTienenAccesoGlobal() {
        Usuario admin = usuario("admin@sintetico.test", "ADMIN", null);
        Usuario explotacion = usuario("explotacion@sintetico.test", "EXPLOTACION", null);
        DocumentoMantenimiento interno = documento(ajeno, EstadoRevisionDocumento.PENDIENTE, VisibilidadDocumento.INTERNO);

        assertThat(servicio.puedeLeerMantenimiento(admin, ajeno)).isTrue();
        assertThat(servicio.puedeLeerMantenimiento(explotacion, ajeno)).isTrue();
        assertThat(servicio.puedeLeerDocumento(explotacion, interno)).isTrue();
        verify(ocupacionRepository, never()).existeOcupacionVigente(anyLong(), anyLong(), any());
    }

    @Test
    void inquilinoLeeMantenimientoDeLocalPropioVigente() {
        Usuario inquilino = usuario("inq@sintetico.test", "INQUILINO", inquilino(true));

        assertThat(servicio.puedeLeerMantenimiento(inquilino, propio)).isTrue();
        verify(ocupacionRepository).existeOcupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY);
    }

    @Test
    void inquilinoNoLeeMantenimientoDeLocalAjeno() {
        Usuario inquilino = usuario("inq@sintetico.test", "INQUILINO", inquilino(true));

        assertThat(servicio.puedeLeerMantenimiento(inquilino, ajeno)).isFalse();
    }

    @Test
    void sinOcupacionVigenteNoHayAcceso() {
        // La consulta excluye ocupaciones inactivas, futuras o con fecha_fin anterior a hoy.
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY)).thenReturn(false);
        Usuario inquilino = usuario("inq@sintetico.test", "INQUILINO", inquilino(true));

        assertThat(servicio.puedeLeerMantenimiento(inquilino, propio)).isFalse();
    }

    @Test
    void hoySeCalculaEnEuropeMadrid() {
        Usuario inquilino = usuario("inq@sintetico.test", "INQUILINO", inquilino(true));

        servicio.puedeLeerMantenimiento(inquilino, propio);

        // El reloj fijo marca 16/11 en UTC: la consulta debe recibir 17/11.
        verify(ocupacionRepository).existeOcupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY);
    }

    @Test
    void usuarioInquilinoSinInquilinoOConInquilinoInactivoNoAccede() {
        Usuario sinInquilino = usuario("huerfano@sintetico.test", "INQUILINO", null);
        Usuario inquilinoInactivo = usuario("inactivo@sintetico.test", "INQUILINO", inquilino(false));

        assertThat(servicio.puedeLeerMantenimiento(sinInquilino, propio)).isFalse();
        assertThat(servicio.puedeLeerMantenimiento(inquilinoInactivo, propio)).isFalse();
    }

    @Test
    void rolDesconocidoNoAccede() {
        Usuario otro = usuario("otro@sintetico.test", "OTRO", inquilino(true));

        assertThat(servicio.puedeLeerMantenimiento(otro, propio)).isFalse();
        assertThatThrownBy(() -> servicio.exigirGestion(otro)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void inquilinoSoloLeeDocumentosCompartidosYValidosDeSuLocal() {
        Usuario inquilino = usuario("inq@sintetico.test", "INQUILINO", inquilino(true));
        DocumentoMantenimiento compartidoPropio = documento(propio, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.COMPARTIDO);
        DocumentoMantenimiento internoPropio = documento(propio, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.INTERNO);
        DocumentoMantenimiento pendientePropio = documento(propio, EstadoRevisionDocumento.PENDIENTE, VisibilidadDocumento.INTERNO);
        DocumentoMantenimiento compartidoAjeno = documento(ajeno, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.COMPARTIDO);

        assertThat(servicio.puedeLeerDocumento(inquilino, compartidoPropio)).isTrue();
        assertThat(servicio.puedeLeerDocumento(inquilino, internoPropio)).isFalse();
        assertThat(servicio.puedeLeerDocumento(inquilino, pendientePropio)).isFalse();
        assertThat(servicio.puedeLeerDocumento(inquilino, compartidoAjeno)).isFalse();
    }

    @Test
    void inquilinoNoPuedeGestionar() {
        Usuario inquilino = usuario("inq@sintetico.test", "INQUILINO", inquilino(true));

        assertThatThrownBy(() -> servicio.exigirGestion(inquilino)).isInstanceOf(AccessDeniedException.class);
    }
}
