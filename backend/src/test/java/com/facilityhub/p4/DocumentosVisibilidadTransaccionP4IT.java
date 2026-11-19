package com.facilityhub.p4;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-005A1 / P4 - Gestion, revision y visibilidad con persistencia real: P4-10, 12, 13, 14, 15, 31.
 * Tras cada peticion se vuelca y se vacia la cache JPA y se lee la fila por JDBC en la misma transaccion,
 * que se revierte al terminar la prueba.
 */
class DocumentosVisibilidadTransaccionP4IT extends P4IntegracionBase {

    @Test
    void p4_10_explotacionListaTodosConVisibilidad() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_AJENO);
        String interno = nombreSintetico("interno.pdf");
        String compartido = nombreSintetico("compartido.pdf");
        insertarDocumento(mantenimiento, interno, "PENDIENTE", "INTERNO", "/no-usado/" + nombreSintetico("a"));
        insertarDocumento(mantenimiento, compartido, "VALIDO", "COMPARTIDO", "/no-usado/" + nombreSintetico("b"));

        MvcResult resultado = mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isOk())
            .andReturn();
        String cuerpo = resultado.getResponse().getContentAsString();
        List<String> nombres = com.jayway.jsonpath.JsonPath.read(cuerpo, "$[*].nombreOriginal");
        List<String> visibilidades = com.jayway.jsonpath.JsonPath.read(cuerpo, "$[?(@.nombreOriginal == '" + interno + "')].visibilidad");
        assertThat(nombres).contains(interno, compartido);
        assertThat(visibilidades).containsExactly("INTERNO");
    }

    @Test
    void p4_12_revisarComoValidoYCompartirPersiste() throws Exception {
        Long documento = insertarDocumento(idMantenimientoDeLocal(LOCAL_PROPIO), nombreSintetico("compartir.pdf"),
            "PENDIENTE", "INTERNO", "/no-usado/" + nombreSintetico("ruta"));
        String token = bearer(EMAIL_EXPLOTACION);

        revisar(documento, "VALIDO", token).andExpect(status().isOk());
        cambiarVisibilidad(documento, "COMPARTIDO", token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibilidad").value("COMPARTIDO"));
        sincronizar();

        assertThat(valorEnBd("estado_revision", documento)).isEqualTo("VALIDO");
        assertThat(valorEnBd("visibilidad", documento)).isEqualTo("COMPARTIDO");
    }

    @Test
    void p4_13_noSeCompartenDocumentosNoValidos() throws Exception {
        String token = bearer(EMAIL_ADMIN);
        for (String estado : new String[] {"PENDIENTE", "RECHAZADO", "CADUCADO"}) {
            Long documento = insertarDocumento(idMantenimientoDeLocal(LOCAL_PROPIO), nombreSintetico(estado + ".pdf"),
                estado, "INTERNO", "/no-usado/" + nombreSintetico("ruta"));
            cambiarVisibilidad(documento, "COMPARTIDO", token).andExpect(status().isBadRequest());
            sincronizar();
            assertThat(valorEnBd("visibilidad", documento)).isEqualTo("INTERNO");
        }
    }

    @Test
    void p4_14_y_15_perderValidoDespublicaYVolverAValidoNoRepublica() throws Exception {
        Long documento = insertarDocumento(idMantenimientoDeLocal(LOCAL_PROPIO), nombreSintetico("despublicar.pdf"),
            "VALIDO", "COMPARTIDO", "/no-usado/" + nombreSintetico("ruta"));
        String token = bearer(EMAIL_EXPLOTACION);

        revisar(documento, "CADUCADO", token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibilidad").value("INTERNO"));
        sincronizar();
        assertThat(valorEnBd("visibilidad", documento)).isEqualTo("INTERNO");

        revisar(documento, "VALIDO", token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibilidad").value("INTERNO"));
        sincronizar();
        assertThat(valorEnBd("estado_revision", documento)).isEqualTo("VALIDO");
        assertThat(valorEnBd("visibilidad", documento)).isEqualTo("INTERNO");
    }

    @Test
    void p4_31_operacionRechazadaNoDejaEscriturasParciales() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        Long documento = insertarDocumento(mantenimiento, nombreSintetico("parcial.pdf"),
            "PENDIENTE", "INTERNO", "/no-usado/" + nombreSintetico("ruta"));
        sincronizar();
        Integer documentosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.documentos_mantenimiento", Integer.class);

        cambiarVisibilidad(documento, "COMPARTIDO", bearer(EMAIL_EXPLOTACION)).andExpect(status().isBadRequest());
        sincronizar();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.documentos_mantenimiento", Integer.class))
            .isEqualTo(documentosAntes);
        assertThat(valorEnBd("estado_revision", documento)).isEqualTo("PENDIENTE");
        assertThat(valorEnBd("visibilidad", documento)).isEqualTo("INTERNO");
    }

    private org.springframework.test.web.servlet.ResultActions revisar(Long documento, String estado, String token) throws Exception {
        return mvc.perform(patch("/api/documentos/{id}/revision", documento)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"estado\":\"" + estado + "\",\"observaciones\":\"P4- revision sintetica\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions cambiarVisibilidad(Long documento, String visibilidad, String token) throws Exception {
        return mvc.perform(patch("/api/documentos/{id}/visibilidad", documento)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"visibilidad\":\"" + visibilidad + "\"}"));
    }
}
