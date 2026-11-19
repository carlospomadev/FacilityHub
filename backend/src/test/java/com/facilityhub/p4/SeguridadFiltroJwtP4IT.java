package com.facilityhub.p4;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FH-005A1 / P4 - Filtro JWT y reglas de URL reales: P4-03..07, P4-29, P4-33. */
class SeguridadFiltroJwtP4IT extends P4IntegracionBase {

    @Test
    void p4_03_sinTokenDevuelve401() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        MvcResult listado = mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
            .andReturn();
        mvc.perform(get("/api/documentos/{id}/archivo", Long.MAX_VALUE))
            .andExpect(status().isUnauthorized());
        assertSinFiltraciones(listado);
    }

    @Test
    void p4_04_tokenConFirmaAjenaOMalFormadoDevuelve401() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, bearerFirmaAjena(EMAIL_EXPLOTACION, "EXPLOTACION")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void p4_05_tokenCaducadoDevuelve401() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        MvcResult resultado = mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, bearerCaducado(EMAIL_EXPLOTACION, "EXPLOTACION")))
            .andExpect(status().isUnauthorized())
            .andReturn();
        assertSinFiltraciones(resultado);
    }

    @Test
    void p4_06_inquilinoNoPuedeSubirYNoSeEscribeNingunArchivo() throws Exception {
        long antes = archivosEnAlmacenamiento();
        MockMultipartFile archivo = new MockMultipartFile("file", nombreSintetico("subida.pdf"), "application/pdf", new byte[] {1, 2, 3});
        mvc.perform(multipart("/api/mantenimientos/{id}/documentos", idMantenimientoDeLocal(LOCAL_PROPIO))
                .file(archivo)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isForbidden());
        assertThat(archivosEnAlmacenamiento()).isEqualTo(antes);
    }

    @Test
    void p4_07_inquilinoNoPuedeRevisarNiCambiarVisibilidad() throws Exception {
        Long documento = insertarDocumento(idMantenimientoDeLocal(LOCAL_PROPIO), nombreSintetico("revision.pdf"),
            "VALIDO", "INTERNO", "/no-usado/" + nombreSintetico("ruta"));
        String token = bearer(EMAIL_INQUILINO);
        mvc.perform(patch("/api/documentos/{id}/revision", documento)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RECHAZADO\",\"observaciones\":\"P4-\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/documentos/{id}/visibilidad", documento)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibilidad\":\"COMPARTIDO\"}"))
            .andExpect(status().isForbidden());
        sincronizar();
        assertThat(valorEnBd("estado_revision", documento)).isEqualTo("VALIDO");
        assertThat(valorEnBd("visibilidad", documento)).isEqualTo("INTERNO");
    }

    @Test
    void p4_29_cuerposDeErrorSinIdentificadoresNiDatosInternos() throws Exception {
        Long ajeno = insertarDocumento(idMantenimientoDeLocal(LOCAL_AJENO), nombreSintetico("ajeno.pdf"),
            "VALIDO", "COMPARTIDO", "/no-usado/" + nombreSintetico("ruta"));
        String token = bearer(EMAIL_INQUILINO);

        MvcResult noVisible = mvc.perform(get("/api/documentos/{id}/archivo", ajeno).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn();
        MvcResult inexistente = mvc.perform(get("/api/documentos/{id}/archivo", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn();

        assertSinFiltraciones(noVisible);
        assertSinFiltraciones(inexistente);
        assertThat(noVisible.getResponse().getContentAsString()).doesNotContain("ajeno.pdf", "/no-usado/");
    }

    @Test
    void p4_33_elServicioRecibeElSubjectDelJwt() throws Exception {
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        // Sujeto valido: el servicio encuentra al usuario y responde.
        mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isOk());
        // Firma valida y rol aceptado por la URL, pero sujeto inexistente en BD: el servicio lo rechaza (403).
        mvc.perform(get("/api/mantenimientos/{id}/documentos", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, bearerSujetoInexistente("EXPLOTACION")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Operacion no permitida"));
    }

    private void assertSinFiltraciones(MvcResult resultado) throws Exception {
        String cuerpo = resultado.getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("@", "ruta_archivo", "rutaArchivo", "observaciones", P4Entorno.ESQUEMA + ".");
    }
}
