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

/** FH-005A1 / P4 - Acceso del inquilino y estado de cuenta leido de BD: P4-08, 09, 24, 25, 27, 28. */
class DocumentosAccesoInquilinoP4IT extends P4IntegracionBase {

    @Test
    void p4_08_usuarioDesactivadoConTokenValidoEsRechazado() throws Exception {
        String token = bearer(EMAIL_INQUILINO);
        jdbc.update("UPDATE facilityhub.usuarios SET activo = false WHERE email = ?", EMAIL_INQUILINO);
        sincronizar();
        // Comportamiento actual documentado: 403 del servicio (no 401).
        mvc.perform(get("/api/mantenimientos/{id}/documentos", idMantenimientoDeLocal(LOCAL_PROPIO))
                .header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Operacion no permitida"));
    }

    @Test
    void p4_09_rolDegradadoEnBdPrevaleceSobreElDelToken() throws Exception {
        String tokenExplotacion = bearer(EMAIL_EXPLOTACION);
        Long documento = insertarDocumento(idMantenimientoDeLocal(LOCAL_PROPIO), nombreSintetico("rol.pdf"),
            "VALIDO", "INTERNO", "/no-usado/" + nombreSintetico("ruta"));
        jdbc.update("""
            UPDATE facilityhub.usuarios SET rol_id = (SELECT id FROM facilityhub.roles WHERE codigo = 'INQUILINO')
            WHERE email = ?""", EMAIL_EXPLOTACION);
        sincronizar();
        // El filtro deja pasar (rol del token), el servicio rechaza (rol vigente en BD).
        mvc.perform(patch("/api/documentos/{id}/visibilidad", documento)
                .header(HttpHeaders.AUTHORIZATION, tokenExplotacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibilidad\":\"COMPARTIDO\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Operacion no permitida"));
        sincronizar();
        assertThat(valorEnBd("visibilidad", documento)).isEqualTo("INTERNO");
    }

    @Test
    void p4_24_inquilinoListaSoloCompartidosYValidosDeSuLocalConDtoMinimo() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        String visible = nombreSintetico("visible.pdf");
        insertarDocumento(mantenimiento, visible, "VALIDO", "COMPARTIDO", "/no-usado/" + nombreSintetico("a"));
        insertarDocumento(mantenimiento, nombreSintetico("interno.pdf"), "VALIDO", "INTERNO", "/no-usado/" + nombreSintetico("b"));
        // Fila incoherente creada a proposito por SQL: comprueba que el filtro exige VALIDO ademas de COMPARTIDO.
        insertarDocumento(mantenimiento, nombreSintetico("pendiente.pdf"), "PENDIENTE", "COMPARTIDO", "/no-usado/" + nombreSintetico("c"));

        MvcResult resultado = mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].observaciones").doesNotExist())
            .andExpect(jsonPath("$[*].visibilidad").doesNotExist())
            .andExpect(jsonPath("$[*].rutaArchivo").doesNotExist())
            .andReturn();

        List<String> nombres = com.jayway.jsonpath.JsonPath.read(resultado.getResponse().getContentAsString(), "$[*].nombreOriginal");
        assertThat(nombres).containsExactly(visible);
    }

    @Test
    void p4_25_inquilinoNoListaUnLocalAjeno() throws Exception {
        mvc.perform(get("/api/mantenimientos/{id}/documentos", idMantenimientoDeLocal(LOCAL_AJENO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"));
    }

    @Test
    void p4_27_descargaPorIdDeDocumentosPropiosNoVisiblesDevuelve404Uniforme() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        String token = bearer(EMAIL_INQUILINO);
        String[][] casos = {{"VALIDO", "INTERNO"}, {"PENDIENTE", "INTERNO"}, {"RECHAZADO", "INTERNO"}, {"CADUCADO", "INTERNO"},
                            {"PENDIENTE", "COMPARTIDO"}};
        for (String[] caso : casos) {
            Long documento = insertarDocumento(mantenimiento, nombreSintetico(caso[0] + ".pdf"), caso[0], caso[1],
                "/no-usado/" + nombreSintetico("ruta"));
            mvc.perform(get("/api/documentos/{id}/archivo", documento).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Recurso no encontrado"));
        }
    }

    // ---- FH-005A2-B1: documentos de local o centro inactivo (aunque sean COMPARTIDO + VALIDO) ----

    @Test
    void b1_08_documentosDeLocalInactivoNoSonAccesibles() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        String nombre = nombreSintetico("local-inactivo.pdf");
        Long documento = insertarDocumento(mantenimiento, nombre, "VALIDO", "COMPARTIDO", "/no-usado/" + nombreSintetico("ruta"));
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE codigo = ?", LOCAL_PROPIO);
        sincronizar();
        assertDocumentoNoAccesible(mantenimiento, documento, nombre);
    }

    @Test
    void b1_09_documentosDeCentroInactivoNoSonAccesibles() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        String nombre = nombreSintetico("centro-inactivo.pdf");
        Long documento = insertarDocumento(mantenimiento, nombre, "VALIDO", "COMPARTIDO", "/no-usado/" + nombreSintetico("ruta"));
        jdbc.update("UPDATE facilityhub.centros SET activo = false WHERE id = (SELECT centro_id FROM facilityhub.locales WHERE codigo = ?)",
            LOCAL_PROPIO);
        sincronizar();
        assertDocumentoNoAccesible(mantenimiento, documento, nombre);
    }

    @Test
    void b1_10_gestionSigueListandoDocumentosDeLocalInactivo() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE codigo = ?", LOCAL_PROPIO);
        sincronizar();
        mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isOk());
    }

    // Listado y descarga: mismo 404 uniforme, sin nombre del documento ni metadatos.
    private void assertDocumentoNoAccesible(Long mantenimiento, Long documento, String nombre) throws Exception {
        String token = bearer(EMAIL_INQUILINO);
        String listado = mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn().getResponse().getContentAsString();
        String descarga = mvc.perform(get("/api/documentos/{id}/archivo", documento).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn().getResponse().getContentAsString();
        assertThat(listado).doesNotContain(nombre, "rutaArchivo", "observaciones");
        assertThat(descarga).doesNotContain(nombre, "rutaArchivo", "observaciones");
    }

    @Test
    void p4_28_descargaAjenaEInexistenteDevuelvenElMismo404() throws Exception {
        Long ajeno = insertarDocumento(idMantenimientoDeLocal(LOCAL_AJENO), nombreSintetico("ajeno.pdf"),
            "VALIDO", "COMPARTIDO", "/no-usado/" + nombreSintetico("ruta"));
        String token = bearer(EMAIL_INQUILINO);
        for (Long id : new Long[] {ajeno, Long.MAX_VALUE}) {
            mvc.perform(get("/api/documentos/{id}/archivo", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Recurso no encontrado"));
        }
    }
}
