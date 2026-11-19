package com.facilityhub.p4;

import com.facilityhub.domain.DocumentoMantenimiento;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FH-005A1 / P4 - Compatibilidad con V4, regresion de datos existentes y acceso al ocupante (FH-005A2-A): P4-02, 30, 32. */
class RegresionYBrechaP4IT extends P4IntegracionBase {

    @Test
    void p4_02_elContextoValidaElEsquemaV4() {
        // El contexto solo existe si Hibernate valido el esquema (ddl-auto=validate) contra la base con V4.
        assertThat(entityManager.getMetamodel().entity(DocumentoMantenimiento.class).getAttribute("visibilidad")).isNotNull();
        Integer columna = jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = 'facilityhub' AND table_name = 'documentos_mantenimiento' AND column_name = 'visibilidad'""",
            Integer.class);
        assertThat(columna).isEqualTo(1);
    }

    @Test
    void p4_30_datosPreexistentesYFh004SeConservan() {
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.documentos_mantenimiento WHERE nombre_original NOT LIKE 'P4-%'", Integer.class);
        Integer internos = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.documentos_mantenimiento WHERE nombre_original NOT LIKE 'P4-%' AND visibilidad = 'INTERNO'",
            Integer.class);
        Integer fh004 = jdbc.queryForObject("""
            SELECT COUNT(*) FROM facilityhub.documentos_mantenimiento
            WHERE nombre_original LIKE 'FH004-%' AND estado_revision = 'PENDIENTE' AND visibilidad = 'INTERNO'""", Integer.class);
        assertThat(total).isEqualTo(6);
        assertThat(internos).isEqualTo(6);
        assertThat(fh004).isEqualTo(5);
    }

    /**
     * FH-005A2-A: sustituye a la caracterizacion anterior (que esperaba 200). Un INQUILINO no obtiene el ocupante
     * de un local ajeno: 404 uniforme y ningun dato del ocupante en el cuerpo.
     */
    @Test
    void p4_32_inquilinoNoVeElOcupanteDeUnLocalAjeno() throws Exception {
        String cuerpo = mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_AJENO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn().getResponse().getContentAsString();
        assertSinDatosDeOcupante(cuerpo);
    }

    @Test
    void p4_32b_inquilinoVeElOcupanteDeSuPropioLocal() throws Exception {
        mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_PROPIO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(idInquilinoDelUsuario(EMAIL_INQUILINO)));
    }

    @Test
    void p4_32c_localInexistenteDaElMismo404QueUnoAjeno() throws Exception {
        String cuerpo = mvc.perform(get("/api/locales/{id}/inquilino-actual", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn().getResponse().getContentAsString();
        assertSinDatosDeOcupante(cuerpo);
        assertThat(cuerpo).doesNotContain(String.valueOf(Long.MAX_VALUE) + "\"");
    }

    @Test
    void p4_32d_ocupacionCaducadaNoDaAcceso() throws Exception {
        fijarFechasOcupacionPropia(null, P4RelojConfig.HOY_MADRID.minusDays(1));
        mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_PROPIO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound());
    }

    @Test
    void p4_32e_ultimoDiaDeOcupacionEsInclusivo() throws Exception {
        fijarFechasOcupacionPropia(null, P4RelojConfig.HOY_MADRID);
        mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_PROPIO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk());
    }

    @Test
    void p4_32f_ocupacionFuturaNoDaAcceso() throws Exception {
        fijarFechasOcupacionPropia(P4RelojConfig.HOY_MADRID.plusDays(1), null);
        mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_PROPIO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound());
    }

    @Test
    void p4_32g_explotacionMantieneElAccesoGlobalProvisional() throws Exception {
        mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_AJENO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(idInquilinoOcupante(LOCAL_AJENO)));
    }

    // ---- FH-005A2-B1: local o centro inactivo ----

    @Test
    void b1_05_inquilinoNoVeElOcupanteDeSuLocalInactivo() throws Exception {
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE id = ?", idLocal(LOCAL_PROPIO));
        sincronizar();
        String cuerpo = mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_PROPIO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn().getResponse().getContentAsString();
        assertSinDatosDeOcupante(cuerpo);
    }

    @Test
    void b1_06_inquilinoNoVeElOcupanteSiElCentroEstaInactivo() throws Exception {
        desactivarCentroDe(LOCAL_PROPIO);
        String cuerpo = mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_PROPIO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn().getResponse().getContentAsString();
        assertSinDatosDeOcupante(cuerpo);
    }

    @Test
    void b1_07_explotacionConservaElOcupanteDeLocalYCentroInactivos() throws Exception {
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE id = ?", idLocal(LOCAL_AJENO));
        desactivarCentroDe(LOCAL_AJENO);
        mvc.perform(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_AJENO))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(idInquilinoOcupante(LOCAL_AJENO)));
    }

    private void desactivarCentroDe(String codigoLocal) {
        jdbc.update("UPDATE facilityhub.centros SET activo = false WHERE id = (SELECT centro_id FROM facilityhub.locales WHERE codigo = ?)",
            codigoLocal);
        sincronizar();
    }

    // Cambia solo la ocupacion demo del local propio, dentro de la transaccion de la prueba (se revierte).
    private void fijarFechasOcupacionPropia(LocalDate inicio, LocalDate fin) {
        Long local = idLocal(LOCAL_PROPIO);
        Long inquilino = idInquilinoDelUsuario(EMAIL_INQUILINO);
        if (inicio != null) {
            jdbc.update("UPDATE facilityhub.ocupaciones_local SET fecha_inicio = ? WHERE local_id = ? AND inquilino_id = ?",
                inicio, local, inquilino);
        }
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET fecha_fin = ? WHERE local_id = ? AND inquilino_id = ?",
            fin, local, inquilino);
        sincronizar();
    }

    private void assertSinDatosDeOcupante(String cuerpo) {
        assertThat(cuerpo).doesNotContain("cif", "razonSocial", "nombreComercial", "emailContacto", "telefono", "@");
    }
}
