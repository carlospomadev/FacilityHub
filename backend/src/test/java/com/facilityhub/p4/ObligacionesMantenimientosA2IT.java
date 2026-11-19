package com.facilityhub.p4;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-005A2-C - Obligaciones y mantenimientos con MockMvc, JWT y JPQL reales sobre facilityhub_fh005_p3.
 * Infraestructura P4 sin cambios (barrera, reloj fijo 17/11/2026 Madrid, rollback por prueba).
 * Datos sinteticos con prefijo P4- dentro de la transaccion revertida; sin binarios.
 * PREPARADA EN A2-C, NO EJECUTADA. El sufijo *A2IT no coincide con los patrones por defecto de Surefire.
 */
class ObligacionesMantenimientosA2IT extends P4IntegracionBase {
    private static final LocalDate HOY = P4RelojConfig.HOY_MADRID;
    private static final String OBSERVACION_INTERNA = "P4- observacion interna de mantenimiento";

    // ---- GET /api/locales/{localId}/obligaciones ----

    @Test
    void a2c_01_inquilinoListaLasObligacionesDeSuLocal() throws Exception {
        Long local = idLocal(LOCAL_PROPIO);
        Integer activas = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.obligaciones_mantenimiento WHERE local_id = ? AND activo", Integer.class, local);
        mvc.perform(get("/api/locales/{id}/obligaciones", local).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(activas)))
            .andExpect(jsonPath("$[*].localId", everyItem(is(local.intValue()))));
    }

    @Test
    void a2c_02_localAjenoInexistenteInactivoOCentroInactivoDan404Uniforme() throws Exception {
        cuerpo404(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_AJENO)), EMAIL_INQUILINO);
        cuerpo404(get("/api/locales/{id}/obligaciones", Long.MAX_VALUE), EMAIL_INQUILINO);

        jdbc.update("UPDATE facilityhub.centros SET activo = false WHERE id = ?", idCentroDe(LOCAL_PROPIO));
        sincronizar();
        cuerpo404(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);

        jdbc.update("UPDATE facilityhub.centros SET activo = true WHERE id = ?", idCentroDe(LOCAL_PROPIO));
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE codigo = ?", LOCAL_PROPIO);
        sincronizar();
        cuerpo404(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
    }

    @Test
    void a2c_03_ocupacionCaducadaFuturaEInactivaEInquilinoInactivoDenegados() throws Exception {
        Object[][] casos = {
            {null, HOY.minusDays(1), true},   // caducada
            {HOY.plusDays(1), null, true},    // futura
            {null, null, false},              // inactiva
        };
        for (Object[] caso : casos) {
            fijarOcupacionPropia((LocalDate) caso[0], (LocalDate) caso[1], (Boolean) caso[2]);
            cuerpo404(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
            cuerpo404(get("/api/obligaciones/{id}/mantenimientos", idObligacionDe(LOCAL_PROPIO)), EMAIL_INQUILINO);
        }
        fijarOcupacionPropia(null, null, true);
        jdbc.update("UPDATE facilityhub.inquilinos SET activo = false WHERE id = ?", idInquilinoDelUsuario(EMAIL_INQUILINO));
        sincronizar();
        cuerpo404(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
    }

    @Test
    void a2c_04_ultimoDiaInclusivoYDiaSiguienteDenegado() throws Exception {
        fijarOcupacionPropia(null, HOY, true);
        String token = bearer(EMAIL_INQUILINO);
        mvc.perform(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk());
        mvc.perform(get("/api/obligaciones/{id}/mantenimientos", idObligacionDe(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk());
        fijarOcupacionPropia(null, HOY.minusDays(1), true);
        cuerpo404(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
    }

    // ---- GET /api/obligaciones/{id}/mantenimientos ----

    @Test
    void a2c_05_inquilinoVeMantenimientosDeSuObligacionSinObservaciones() throws Exception {
        Long obligacion = idObligacionDe(LOCAL_PROPIO);
        Long sintetico = insertarMantenimiento(obligacion, OBSERVACION_INTERNA);
        String cuerpo = mvc.perform(get("/api/obligaciones/{id}/mantenimientos", obligacion)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(sintetico.intValue())))
            .andExpect(jsonPath("$[*].obligacionId", everyItem(is(obligacion.intValue()))))
            .andExpect(jsonPath("$[*].observaciones").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain(OBSERVACION_INTERNA, "observaciones");
    }

    @Test
    void a2c_06_obligacionAjenaEInexistenteDanElMismo404SinDatos() throws Exception {
        Long ajena = idObligacionDe(LOCAL_AJENO);
        insertarMantenimiento(ajena, OBSERVACION_INTERNA);
        String cuerpoAjena = cuerpo404(get("/api/obligaciones/{id}/mantenimientos", ajena), EMAIL_INQUILINO);
        String cuerpoInexistente = cuerpo404(get("/api/obligaciones/{id}/mantenimientos", Long.MAX_VALUE), EMAIL_INQUILINO);
        // "instance" repite la ruta pedida por el propio cliente; el resto del cuerpo debe ser identico.
        assertThat(cuerpoAjena.replace(String.valueOf(ajena), "ID"))
            .isEqualTo(cuerpoInexistente.replace(String.valueOf(Long.MAX_VALUE), "ID"));
        assertThat(cuerpoAjena).doesNotContain(OBSERVACION_INTERNA, "Obligacion no encontrada");
    }

    @Test
    void a2c_07_obligacionPropiaInactivaNoSeExponeAlInquilino() throws Exception {
        Long obligacion = idObligacionDe(LOCAL_PROPIO);
        jdbc.update("UPDATE facilityhub.obligaciones_mantenimiento SET activo = false WHERE id = ?", obligacion);
        sincronizar();
        cuerpo404(get("/api/obligaciones/{id}/mantenimientos", obligacion), EMAIL_INQUILINO);
        mvc.perform(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", not(hasItem(obligacion.intValue()))));
    }

    @Test
    void a2c_08_gestionConservaAccesoGlobalYObservaciones() throws Exception {
        Long ajena = idObligacionDe(LOCAL_AJENO);
        insertarMantenimiento(ajena, OBSERVACION_INTERNA);
        for (String email : new String[] {EMAIL_ADMIN, EMAIL_EXPLOTACION}) {
            mvc.perform(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_AJENO)).header(HttpHeaders.AUTHORIZATION, bearer(email)))
                .andExpect(status().isOk());
            mvc.perform(get("/api/obligaciones/{id}/mantenimientos", ajena).header(HttpHeaders.AUTHORIZATION, bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].observaciones", hasItem(OBSERVACION_INTERNA)));
        }
    }

    // ---- Escrituras ----

    @Test
    void a2c_09_inquilinoNoPuedeCrearNiCambiarEstadoYNoSeEscribe() throws Exception {
        Long obligacion = idObligacionDe(LOCAL_PROPIO);
        Long mantenimiento = insertarMantenimiento(obligacion, null);
        int antes = contarMantenimientos();
        String token = bearer(EMAIL_INQUILINO);
        mvc.perform(crear(obligacion).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(cambiarEstado(mantenimiento, "EN_PROCESO", null).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden());
        sincronizar();
        assertThat(contarMantenimientos()).isEqualTo(antes);
        assertThat(estadoEnBd(mantenimiento)).isEqualTo("PROGRAMADO");
    }

    @Test
    void a2c_10_tokenExplotacionConRolDegradadoEnBdRecibe403SinEscribir() throws Exception {
        Long obligacion = idObligacionDe(LOCAL_PROPIO);
        Long mantenimiento = insertarMantenimiento(obligacion, null);
        String tokenExplotacion = bearer(EMAIL_EXPLOTACION);
        jdbc.update("""
            UPDATE facilityhub.usuarios SET rol_id = (SELECT id FROM facilityhub.roles WHERE codigo = 'INQUILINO')
            WHERE email = ?""", EMAIL_EXPLOTACION);
        sincronizar();
        int antes = contarMantenimientos();
        // El filtro deja pasar (rol del token); el servicio rechaza (rol vigente en BD) antes de escribir.
        mvc.perform(crear(obligacion).header(HttpHeaders.AUTHORIZATION, tokenExplotacion))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Operacion no permitida"));
        mvc.perform(cambiarEstado(mantenimiento, "EN_PROCESO", null).header(HttpHeaders.AUTHORIZATION, tokenExplotacion))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Operacion no permitida"));
        sincronizar();
        assertThat(contarMantenimientos()).isEqualTo(antes);
        assertThat(estadoEnBd(mantenimiento)).isEqualTo("PROGRAMADO");
    }

    @Test
    void a2c_11_usuarioDesactivadoConTokenValidoRecibe403() throws Exception {
        Long obligacion = idObligacionDe(LOCAL_PROPIO);
        String tokenAdmin = bearer(EMAIL_ADMIN);
        String tokenInquilino = bearer(EMAIL_INQUILINO);
        jdbc.update("UPDATE facilityhub.usuarios SET activo = false WHERE email IN (?, ?)", EMAIL_ADMIN, EMAIL_INQUILINO);
        sincronizar();
        int antes = contarMantenimientos();
        mvc.perform(crear(obligacion).header(HttpHeaders.AUTHORIZATION, tokenAdmin))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("Operacion no permitida"));
        mvc.perform(get("/api/locales/{id}/obligaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, tokenInquilino))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/obligaciones/{id}/mantenimientos", obligacion).header(HttpHeaders.AUTHORIZATION, tokenInquilino))
            .andExpect(status().isForbidden());
        sincronizar();
        assertThat(contarMantenimientos()).isEqualTo(antes);
    }

    @Test
    void a2c_12_jwtAusenteInvalidoOCaducadoDevuelve401() throws Exception {
        Long obligacion = idObligacionDe(LOCAL_PROPIO);
        Long local = idLocal(LOCAL_PROPIO);
        mvc.perform(get("/api/locales/{id}/obligaciones", local)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/obligaciones/{id}/mantenimientos", obligacion)).andExpect(status().isUnauthorized());
        mvc.perform(crear(obligacion)).andExpect(status().isUnauthorized());
        mvc.perform(cambiarEstado(Long.MAX_VALUE, "EN_PROCESO", null)).andExpect(status().isUnauthorized());
        mvc.perform(crear(obligacion).header(HttpHeaders.AUTHORIZATION, bearerFirmaAjena(EMAIL_ADMIN, "ADMIN")))
            .andExpect(status().isUnauthorized());
        mvc.perform(crear(obligacion).header(HttpHeaders.AUTHORIZATION, bearerCaducado(EMAIL_ADMIN, "ADMIN")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/locales/{id}/obligaciones", local).header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void a2c_13_gestionConservaCreacionTransicionYProximaFecha() throws Exception {
        Long obligacion = idObligacionDe(LOCAL_PROPIO);
        int antes = contarMantenimientos();
        mvc.perform(crear(obligacion).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
            .andExpect(jsonPath("$.obligacionId").value(obligacion));
        sincronizar();
        assertThat(contarMantenimientos()).isEqualTo(antes + 1);

        Long mantenimiento = insertarMantenimiento(obligacion, null);
        Integer periodicidad = jdbc.queryForObject("""
            SELECT t.periodicidad_meses FROM facilityhub.obligaciones_mantenimiento o
            JOIN facilityhub.tipos_mantenimiento t ON t.id = o.tipo_mantenimiento_id WHERE o.id = ?""", Integer.class, obligacion);
        LocalDate realizacion = HOY.minusDays(2);
        mvc.perform(cambiarEstado(mantenimiento, "FINALIZADO", realizacion).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("FINALIZADO"))
            .andExpect(jsonPath("$.observaciones").value("P4- cierre sintetico"));
        sincronizar();
        LocalDate proxima = jdbc.queryForObject(
            "SELECT proxima_fecha FROM facilityhub.obligaciones_mantenimiento WHERE id = ?", LocalDate.class, obligacion);
        assertThat(proxima).isEqualTo(realizacion.plusMonths(periodicidad));

        // FINALIZADO sin fecha sigue rechazandose (400) y no cambia nada.
        Long otro = insertarMantenimiento(obligacion, null);
        mvc.perform(cambiarEstado(otro, "FINALIZADO", null).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isBadRequest());
        sincronizar();
        assertThat(estadoEnBd(otro)).isEqualTo("PROGRAMADO");
    }

    @Test
    void a2c_14_gestionConservaSus404PreviosEnEscrituras() throws Exception {
        mvc.perform(crear(Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isNotFound());
        mvc.perform(cambiarEstado(Long.MAX_VALUE, "EN_PROCESO", null).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isNotFound());
    }

    // ---- Apoyo ----

    // 404 uniforme: mismo titulo/detalle y sin datos internos ni de terceros; devuelve el cuerpo.
    private String cuerpo404(MockHttpServletRequestBuilder peticion, String email) throws Exception {
        String cuerpo = mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, bearer(email)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"))
            .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("observaciones", "descripcion", "cif", "@", "codigo", OBSERVACION_INTERNA);
        return cuerpo;
    }

    private MockHttpServletRequestBuilder crear(Long obligacionId) {
        return post("/api/obligaciones/{id}/mantenimientos", obligacionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fechaProgramada\":\"" + HOY.plusDays(30) + "\",\"descripcion\":\"" + nombreSintetico("alta") + "\"}");
    }

    private MockHttpServletRequestBuilder cambiarEstado(Long mantenimientoId, String estado, LocalDate realizacion) {
        String fecha = realizacion == null ? "null" : "\"" + realizacion + "\"";
        return patch("/api/mantenimientos/{id}/estado", mantenimientoId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"estado\":\"" + estado + "\",\"fechaRealizacion\":" + fecha + ",\"observaciones\":\"P4- cierre sintetico\"}");
    }

    private Long idCentroDe(String codigoLocal) {
        return jdbc.queryForObject("SELECT centro_id FROM facilityhub.locales WHERE codigo = ?", Long.class, codigoLocal);
    }

    private Long idObligacionDe(String codigoLocal) {
        return jdbc.queryForObject("""
            SELECT MIN(o.id) FROM facilityhub.obligaciones_mantenimiento o
            JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.activo""", Long.class, codigoLocal);
    }

    private Long insertarMantenimiento(Long obligacionId, String observaciones) {
        Long id = jdbc.queryForObject("""
            INSERT INTO facilityhub.mantenimientos (obligacion_id, fecha_programada, estado, descripcion, observaciones)
            VALUES (?, ?, 'PROGRAMADO', ?, ?) RETURNING id""",
            Long.class, obligacionId, HOY.plusDays(5), nombreSintetico("mant"), observaciones);
        sincronizar();
        return id;
    }

    private int contarMantenimientos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.mantenimientos", Integer.class);
    }

    private String estadoEnBd(Long mantenimientoId) {
        return jdbc.queryForObject("SELECT estado FROM facilityhub.mantenimientos WHERE id = ?", String.class, mantenimientoId);
    }

    // Solo la ocupacion demo del local propio, dentro de la transaccion (se revierte). inicio nulo = conservar el demo.
    private LocalDate inicioOriginal;

    private void fijarOcupacionPropia(LocalDate inicio, LocalDate fin, boolean activo) {
        Long local = idLocal(LOCAL_PROPIO);
        Long inquilino = idInquilinoDelUsuario(EMAIL_INQUILINO);
        if (inicioOriginal == null) {
            inicioOriginal = jdbc.queryForObject(
                "SELECT fecha_inicio FROM facilityhub.ocupaciones_local WHERE local_id = ? AND inquilino_id = ?",
                LocalDate.class, local, inquilino);
        }
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET fecha_fin = NULL WHERE local_id = ? AND inquilino_id = ?", local, inquilino);
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET fecha_inicio = ?, fecha_fin = ?, activo = ? WHERE local_id = ? AND inquilino_id = ?",
            inicio == null ? inicioOriginal : inicio, fin, activo, local, inquilino);
        sincronizar();
    }
}
