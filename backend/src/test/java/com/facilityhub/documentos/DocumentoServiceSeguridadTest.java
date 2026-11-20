package com.facilityhub.documentos;

import com.facilityhub.api.dto.documento.CambiarVisibilidadRequest;
import com.facilityhub.api.dto.documento.DocumentoDto;
import com.facilityhub.api.dto.documento.DocumentoInquilinoDto;
import com.facilityhub.api.dto.documento.DocumentoRespuesta;
import com.facilityhub.api.dto.documento.RevisarDocumentoRequest;
import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoRevisionDocumento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.domain.VisibilidadDocumento;
import com.facilityhub.repository.DocumentoMantenimientoRepository;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.repository.UsuarioRepository;
import com.facilityhub.service.DocumentStorageService;
import com.facilityhub.service.DocumentoAutorizacionService;
import com.facilityhub.service.DocumentoService;
import com.facilityhub.service.RecursoNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static com.facilityhub.documentos.DocumentosFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Servicio documental con la autorizacion real y repositorios simulados (sin BD ni Spring).
class DocumentoServiceSeguridadTest {
    private static final String ADMIN = "admin@sintetico.test";
    private static final String EXPLOTACION = "explotacion@sintetico.test";
    private static final String INQUILINO = "inquilino@sintetico.test";
    private static final Long DOC_INEXISTENTE = 999_999L;

    private DocumentoMantenimientoRepository documentoRepository;
    private MantenimientoRepository mantenimientoRepository;
    private DocumentStorageService storageService;
    private DocumentoService servicio;

    private final Mantenimiento propio = mantenimiento(MANTENIMIENTO_PROPIO, LOCAL_PROPIO);
    private final Mantenimiento ajeno = mantenimiento(MANTENIMIENTO_AJENO, LOCAL_AJENO);

    @BeforeEach
    void preparar() {
        documentoRepository = mock(DocumentoMantenimientoRepository.class);
        mantenimientoRepository = mock(MantenimientoRepository.class);
        storageService = mock(DocumentStorageService.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        OcupacionLocalRepository ocupacionRepository = mock(OcupacionLocalRepository.class);

        Usuario admin = usuario(ADMIN, "ADMIN", null);
        Usuario explotacion = usuario(EXPLOTACION, "EXPLOTACION", null);
        Usuario inquilino = usuario(INQUILINO, "INQUILINO", inquilino(true));
        when(usuarioRepository.findByEmailAndActivoTrue(ADMIN)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByEmailAndActivoTrue(EXPLOTACION)).thenReturn(Optional.of(explotacion));
        when(usuarioRepository.findByEmailAndActivoTrue(INQUILINO)).thenReturn(Optional.of(inquilino));
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY)).thenReturn(true);
        when(ocupacionRepository.existeOcupacionVigente(LOCAL_AJENO, INQUILINO_ID, HOY)).thenReturn(false);
        // P6-C2: ocupacion vigente concreta que cubre el mantenimiento propio.
        var ocupacion = ocupacionVigente(6001L);
        when(ocupacionRepository.ocupacionVigente(LOCAL_PROPIO, INQUILINO_ID, HOY)).thenReturn(Optional.of(ocupacion));

        when(mantenimientoRepository.findById(MANTENIMIENTO_PROPIO)).thenReturn(Optional.of(propio));
        when(mantenimientoRepository.findById(MANTENIMIENTO_AJENO)).thenReturn(Optional.of(ajeno));
        when(mantenimientoRepository.findById(DOC_INEXISTENTE)).thenReturn(Optional.empty());
        when(documentoRepository.findById(DOC_INEXISTENTE)).thenReturn(Optional.empty());

        DocumentoAutorizacionService autorizacion =
            new DocumentoAutorizacionService(usuarioRepository, ocupacionRepository, RELOJ);
        servicio = new DocumentoService(documentoRepository, mantenimientoRepository, storageService, autorizacion);
    }

    @Test
    void explotacionListaTodosLosDocumentosConDatosCompletos() {
        DocumentoMantenimiento interno = documento(ajeno, EstadoRevisionDocumento.PENDIENTE, VisibilidadDocumento.INTERNO);
        when(documentoRepository.findByMantenimientoIdOrderByFechaSubidaDesc(MANTENIMIENTO_AJENO)).thenReturn(List.of(interno));

        List<DocumentoRespuesta> lista = servicio.listar(MANTENIMIENTO_AJENO, EXPLOTACION);

        assertThat(lista).singleElement().isInstanceOf(DocumentoDto.class);
        DocumentoDto dto = (DocumentoDto) lista.get(0);
        assertThat(dto.visibilidad()).isEqualTo("INTERNO");
        assertThat(dto.observaciones()).isEqualTo("Observacion interna sintetica");
    }

    @Test
    void inquilinoListaSoloCompartidosValidosConDtoMinimo() {
        DocumentoMantenimiento compartido = documento(propio, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.COMPARTIDO);
        when(documentoRepository.findByMantenimientoIdAndVisibilidadAndEstadoRevisionOrderByFechaSubidaDesc(
            MANTENIMIENTO_PROPIO, VisibilidadDocumento.COMPARTIDO, EstadoRevisionDocumento.VALIDO))
            .thenReturn(List.of(compartido));

        List<DocumentoRespuesta> lista = servicio.listar(MANTENIMIENTO_PROPIO, INQUILINO);

        assertThat(lista).singleElement().isInstanceOf(DocumentoInquilinoDto.class);
        // El filtro se aplica en la consulta: nunca se cargan los internos.
        verify(documentoRepository, never()).findByMantenimientoIdOrderByFechaSubidaDesc(anyLong());
        assertThat(DocumentoInquilinoDto.class.getRecordComponents())
            .extracting(c -> c.getName())
            .doesNotContain("observaciones", "visibilidad", "rutaArchivo");
    }

    @Test
    void inquilinoNoListaMantenimientoAjenoNiInexistenteYAmbosDanElMismoError() {
        assertThatThrownBy(() -> servicio.listar(MANTENIMIENTO_AJENO, INQUILINO))
            .isInstanceOf(RecursoNoDisponibleException.class)
            .hasMessage("Recurso no encontrado");
        assertThatThrownBy(() -> servicio.listar(DOC_INEXISTENTE, INQUILINO))
            .isInstanceOf(RecursoNoDisponibleException.class)
            .hasMessage("Recurso no encontrado");
    }

    @Test
    void inquilinoDescargaCompartidoValidoDeSuLocal() {
        DocumentoMantenimiento compartido = documento(propio, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.COMPARTIDO);
        when(documentoRepository.findById(1L)).thenReturn(Optional.of(compartido));
        Resource recurso = mock(Resource.class);
        when(storageService.cargar(compartido.getRutaArchivo())).thenReturn(recurso);

        DocumentoService.DescargaDocumento descarga = servicio.obtenerDescarga(1L, INQUILINO);

        assertThat(descarga.resource()).isSameAs(recurso);
    }

    @Test
    void inquilinoNoDescargaInternoPorIdDirectoNiLeeElArchivo() {
        DocumentoMantenimiento interno = documento(propio, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.INTERNO);
        when(documentoRepository.findById(2L)).thenReturn(Optional.of(interno));

        assertThatThrownBy(() -> servicio.obtenerDescarga(2L, INQUILINO))
            .isInstanceOf(RecursoNoDisponibleException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void inquilinoNoDescargaCompartidoDeLocalAjeno() {
        DocumentoMantenimiento compartidoAjeno = documento(ajeno, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.COMPARTIDO);
        when(documentoRepository.findById(3L)).thenReturn(Optional.of(compartidoAjeno));

        assertThatThrownBy(() -> servicio.obtenerDescarga(3L, INQUILINO))
            .isInstanceOf(RecursoNoDisponibleException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void descargaInexistenteDaElMismoErrorQueAjenoOInterno() {
        assertThatThrownBy(() -> servicio.obtenerDescarga(DOC_INEXISTENTE, INQUILINO))
            .isInstanceOf(RecursoNoDisponibleException.class)
            .hasMessage("Recurso no encontrado");
    }

    @Test
    void inquilinoNoPuedeSubirYNoSeEscribeNada() {
        MockMultipartFile archivo = new MockMultipartFile("file", "SINTETICO.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> servicio.subir(MANTENIMIENTO_PROPIO, archivo, null, null, null, INQUILINO))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(storageService);
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void explotacionSubeYElDocumentoNaceInterno() {
        MockMultipartFile archivo = new MockMultipartFile("file", "SINTETICO.pdf", "application/pdf", new byte[] {1});
        when(storageService.guardar(MANTENIMIENTO_PROPIO, archivo)).thenReturn("/almacen/sintetico/nuevo.pdf");
        when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DocumentoDto dto = servicio.subir(MANTENIMIENTO_PROPIO, archivo, null, null, null, EXPLOTACION);

        assertThat(dto.visibilidad()).isEqualTo("INTERNO");
        assertThat(dto.estadoRevision()).isEqualTo("PENDIENTE");
    }

    @Test
    void inquilinoNoPuedeRevisarNiCambiarVisibilidad() {
        assertThatThrownBy(() -> servicio.revisar(1L, new RevisarDocumentoRequest(EstadoRevisionDocumento.VALIDO, null), INQUILINO))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.cambiarVisibilidad(1L, new CambiarVisibilidadRequest(VisibilidadDocumento.COMPARTIDO), INQUILINO))
            .isInstanceOf(AccessDeniedException.class);
        verify(documentoRepository, never()).findById(1L);
    }

    @Test
    void explotacionCompartePeroNoUnDocumentoNoValido() {
        DocumentoMantenimiento valido = documento(propio, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.INTERNO);
        DocumentoMantenimiento pendiente = documento(propio, EstadoRevisionDocumento.PENDIENTE, VisibilidadDocumento.INTERNO);
        when(documentoRepository.findById(4L)).thenReturn(Optional.of(valido));
        when(documentoRepository.findById(5L)).thenReturn(Optional.of(pendiente));

        DocumentoDto dto = servicio.cambiarVisibilidad(4L, new CambiarVisibilidadRequest(VisibilidadDocumento.COMPARTIDO), EXPLOTACION);

        assertThat(dto.visibilidad()).isEqualTo("COMPARTIDO");
        assertThatThrownBy(() -> servicio.cambiarVisibilidad(5L, new CambiarVisibilidadRequest(VisibilidadDocumento.COMPARTIDO), ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revisarComoRechazadoDespublicaYElInquilinoDejaDeVerlo() {
        DocumentoMantenimiento compartido = documento(propio, EstadoRevisionDocumento.VALIDO, VisibilidadDocumento.COMPARTIDO);
        when(documentoRepository.findById(6L)).thenReturn(Optional.of(compartido));

        DocumentoDto dto = servicio.revisar(6L, new RevisarDocumentoRequest(EstadoRevisionDocumento.RECHAZADO, "sintetico"), EXPLOTACION);

        assertThat(dto.visibilidad()).isEqualTo("INTERNO");
        assertThatThrownBy(() -> servicio.obtenerDescarga(6L, INQUILINO))
            .isInstanceOf(RecursoNoDisponibleException.class);
    }

    @Test
    void usuarioDadoDeBajaConTokenVigenteNoAccede() {
        assertThatThrownBy(() -> servicio.listar(MANTENIMIENTO_PROPIO, "baja@sintetico.test"))
            .isInstanceOf(AccessDeniedException.class);
    }
}
