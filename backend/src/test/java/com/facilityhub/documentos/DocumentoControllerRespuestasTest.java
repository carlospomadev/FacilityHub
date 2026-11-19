package com.facilityhub.documentos;

import com.facilityhub.api.ApiExceptionHandler;
import com.facilityhub.api.DocumentoController;
import com.facilityhub.api.dto.documento.DocumentoDto;
import com.facilityhub.api.dto.documento.DocumentoInquilinoDto;
import com.facilityhub.api.dto.documento.DocumentoRespuesta;
import com.facilityhub.service.DocumentoService;
import com.facilityhub.service.RecursoNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// MockMvc standalone: controlador + manejador de errores, sin contexto Spring, filtros JWT ni BD.
// Las reglas de URL de SecurityConfig (401/403 por filtro) no se cubren aqui: requieren spring-security-test.
class DocumentoControllerRespuestasTest {
    private static final Principal INQUILINO = () -> "inquilino@sintetico.test";
    private static final Principal EXPLOTACION = () -> "explotacion@sintetico.test";

    private DocumentoService servicio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(DocumentoService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DocumentoController(servicio))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void el404EsIdenticoParaInexistenteAjenoEInterno() throws Exception {
        when(servicio.obtenerDescarga(eq(1L), any())).thenThrow(new RecursoNoDisponibleException());
        when(servicio.obtenerDescarga(eq(2L), any())).thenThrow(new RecursoNoDisponibleException());

        // "instance" lo rellena Spring con la ruta pedida por el propio cliente; el resto debe coincidir.
        for (long id : new long[] {1L, 2L}) {
            mvc.perform(get("/api/documentos/" + id + "/archivo").principal(INQUILINO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Recurso no encontrado"));
        }
    }

    @Test
    void operacionNoPermitidaDevuelve403SinDetalles() throws Exception {
        when(servicio.cambiarVisibilidad(eq(5L), any(), any())).thenThrow(new AccessDeniedException("motivo interno"));

        mvc.perform(patch("/api/documentos/5/visibilidad").principal(INQUILINO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibilidad\":\"COMPARTIDO\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Operacion no permitida"));
    }

    @Test
    void visibilidadInvalidaOAusenteSeRechaza() throws Exception {
        mvc.perform(patch("/api/documentos/5/visibilidad").principal(EXPLOTACION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibilidad\":\"PUBLICO\"}"))
            .andExpect(status().is4xxClientError());
        mvc.perform(patch("/api/documentos/5/visibilidad").principal(EXPLOTACION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void elListadoDelInquilinoNoSerializaObservacionesNiVisibilidad() throws Exception {
        List<DocumentoRespuesta> lista = List.of(new DocumentoInquilinoDto(
            10L, 7001L, "SINTETICO.pdf", null, null, null, "VALIDO", LocalDateTime.of(2026, 11, 17, 10, 0)));
        when(servicio.listar(eq(7001L), any())).thenReturn(lista);

        mvc.perform(get("/api/mantenimientos/7001/documentos").principal(INQUILINO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombreOriginal").value("SINTETICO.pdf"))
            .andExpect(jsonPath("$[0].estadoRevision").value("VALIDO"))
            .andExpect(jsonPath("$[0].observaciones").doesNotExist())
            .andExpect(jsonPath("$[0].visibilidad").doesNotExist());
    }

    @Test
    void elListadoDeGestionMantieneLosCamposQueUsaAndroid() throws Exception {
        List<DocumentoRespuesta> lista = List.of(new DocumentoDto(
            11L, 7001L, "SINTETICO.pdf", null, null, null, "PENDIENTE", "nota", LocalDateTime.of(2026, 11, 17, 10, 0), "INTERNO"));
        when(servicio.listar(eq(7001L), any())).thenReturn(lista);

        mvc.perform(get("/api/mantenimientos/7001/documentos").principal(EXPLOTACION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(11))
            .andExpect(jsonPath("$[0].mantenimientoId").value(7001))
            .andExpect(jsonPath("$[0].estadoRevision").value("PENDIENTE"))
            .andExpect(jsonPath("$[0].fechaSubida").exists())
            .andExpect(jsonPath("$[0].visibilidad").value("INTERNO"));
    }

    @Test
    void elControladorPasaElSubjectDelPrincipalAlServicio() throws Exception {
        when(servicio.listar(eq(7001L), eq("inquilino@sintetico.test"))).thenReturn(List.of());

        mvc.perform(get("/api/mantenimientos/7001/documentos").principal(INQUILINO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
