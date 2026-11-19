package com.facilityhub.p4;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-005A2-B - Centros, locales e instalaciones con MockMvc, JWT y JPQL reales sobre facilityhub_fh005_p3.
 * Infraestructura P4 sin cambios (barrera, reloj fijo 17/11/2026 Madrid, rollback por prueba).
 * Los datos demo tienen un unico centro con L-101 (propio) y L-205 (de otro inquilino); los centros ajenos se
 * fabrican sinteticamente con prefijo P4- dentro de la transaccion revertida.
 * PREPARADA EN A2-B, NO EJECUTADA. El sufijo *A2IT no coincide con los patrones por defecto de Surefire.
 */
class CentrosLocalesInstalacionesA2IT extends P4IntegracionBase {
    private static final LocalDate HOY = P4RelojConfig.HOY_MADRID;

    // ---- GET /api/centros ----

    @Test
    void a2b_01_inquilinoVeSoloSuCentroSinDuplicados() throws Exception {
        Long centroPropio = idCentroDe(LOCAL_PROPIO);
        // Segundo local propio en el mismo centro: el centro no debe repetirse.
        Long segundo = crearLocal(centroPropio, true);
        ocupar(segundo, idInquilinoDelUsuario(EMAIL_INQUILINO), HOY.minusDays(10), null, true);
        // Centro ajeno con ocupacion de otro inquilino: no debe aparecer.
        Long centroAjeno = crearCentro(true);
        ocupar(crearLocal(centroAjeno, true), crearInquilino(), HOY.minusDays(10), null, true);
        sincronizar();

        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(centroPropio));
    }

    @Test
    void a2b_02_inquilinoSinOcupacionVigenteRecibeListaVacia() throws Exception {
        fijarOcupacionPropia(null, HOY.minusDays(1), true);
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void a2b_03_gestionConservaElListadoGlobalDeCentrosActivos() throws Exception {
        Long centroAjeno = crearCentro(true);
        sincronizar();
        Integer activos = jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.centros WHERE activo", Integer.class);
        for (String email : new String[] {EMAIL_ADMIN, EMAIL_EXPLOTACION}) {
            mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(activos)))
                .andExpect(jsonPath("$[*].id", hasItems(idCentroDe(LOCAL_PROPIO).intValue(), centroAjeno.intValue())));
        }
    }

    // ---- GET /api/centros/{id} ----

    @Test
    void a2b_04_inquilinoVeElDetalleDeSuCentro() throws Exception {
        mvc.perform(get("/api/centros/{id}", idCentroDe(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(idCentroDe(LOCAL_PROPIO)));
    }

    @Test
    void a2b_05_centroAjenoEInexistenteDanElMismo404SinDatos() throws Exception {
        Long centroAjeno = crearCentro(true);
        ocupar(crearLocal(centroAjeno, true), crearInquilino(), HOY.minusDays(10), null, true);
        sincronizar();
        String codigoAjeno = jdbc.queryForObject("SELECT codigo FROM facilityhub.centros WHERE id = ?", String.class, centroAjeno);

        // "instance" repite la ruta pedida por el propio cliente; el resto del cuerpo debe ser identico.
        String ajeno = cuerpo404(get("/api/centros/{id}", centroAjeno), EMAIL_INQUILINO);
        String inexistente = cuerpo404(get("/api/centros/{id}", Long.MAX_VALUE), EMAIL_INQUILINO);

        assertThat(ajeno).doesNotContain(codigoAjeno, "Centro no encontrado");
        assertThat(ajeno.replace(String.valueOf(centroAjeno), "ID"))
            .isEqualTo(inexistente.replace(String.valueOf(Long.MAX_VALUE), "ID"));
    }

    @Test
    void a2b_06_centroInactivoNoEsVisibleParaElInquilinoPeroSiParaGestion() throws Exception {
        Long centroPropio = idCentroDe(LOCAL_PROPIO);
        jdbc.update("UPDATE facilityhub.centros SET activo = false WHERE id = ?", centroPropio);
        sincronizar();

        cuerpo404(get("/api/centros/{id}", centroPropio), EMAIL_INQUILINO);
        cuerpo404(get("/api/centros/{id}/locales", centroPropio), EMAIL_INQUILINO);
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        // Semantica previa de gestion: el detalle incluye centros inactivos.
        mvc.perform(get("/api/centros/{id}", centroPropio).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activo").value(false));
    }

    // ---- GET /api/centros/{id}/locales ----

    @Test
    void a2b_07_inquilinoSoloVeSusLocalesEnUnCentroCompartido() throws Exception {
        mvc.perform(get("/api/centros/{id}/locales", idCentroDe(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].codigo").value(LOCAL_PROPIO))
            .andExpect(jsonPath("$[*].codigo", not(hasItem(LOCAL_AJENO))));
    }

    @Test
    void a2b_08_localesDeCentroAjenoOInexistenteDan404() throws Exception {
        Long centroAjeno = crearCentro(true);
        ocupar(crearLocal(centroAjeno, true), crearInquilino(), HOY.minusDays(10), null, true);
        sincronizar();
        cuerpo404(get("/api/centros/{id}/locales", centroAjeno), EMAIL_INQUILINO);
        cuerpo404(get("/api/centros/{id}/locales", Long.MAX_VALUE), EMAIL_INQUILINO);
    }

    @Test
    void a2b_09_gestionVeTodosLosLocalesActivosDelCentro() throws Exception {
        for (String email : new String[] {EMAIL_ADMIN, EMAIL_EXPLOTACION}) {
            mvc.perform(get("/api/centros/{id}/locales", idCentroDe(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", hasItems(LOCAL_PROPIO, LOCAL_AJENO)));
        }
    }

    // ---- GET /api/locales/{id}/instalaciones ----

    @Test
    void a2b_10_inquilinoVeLasInstalacionesDeSuLocal() throws Exception {
        String nombre = nombreSintetico("inst");
        jdbc.update("INSERT INTO facilityhub.instalaciones (local_id, nombre, tipo) VALUES (?, ?, 'P4-TIPO')", idLocal(LOCAL_PROPIO), nombre);
        sincronizar();
        mvc.perform(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].nombre", hasItem(nombre)));
    }

    @Test
    void a2b_11_instalacionesDeLocalAjenoOInexistenteDanElMismo404() throws Exception {
        String ajeno = cuerpo404(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_AJENO)), EMAIL_INQUILINO);
        cuerpo404(get("/api/locales/{id}/instalaciones", Long.MAX_VALUE), EMAIL_INQUILINO);
        assertThat(ajeno).doesNotContain(LOCAL_AJENO);
    }

    @Test
    void a2b_12_gestionVeInstalacionesDeCualquierLocal() throws Exception {
        for (String email : new String[] {EMAIL_ADMIN, EMAIL_EXPLOTACION}) {
            mvc.perform(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_AJENO)).header(HttpHeaders.AUTHORIZATION, bearer(email)))
                .andExpect(status().isOk());
        }
    }

    // ---- Vigencia de la ocupacion (centros, locales e instalaciones) ----

    @Test
    void a2b_13_ocupacionCaducadaFuturaEInactivaNoDanAcceso() throws Exception {
        Object[][] casos = {
            {null, HOY.minusDays(1), true},   // caducada
            {HOY.plusDays(1), null, true},    // futura
            {null, null, false},              // inactiva
        };
        Long centro = idCentroDe(LOCAL_PROPIO);
        for (Object[] caso : casos) {
            fijarOcupacionPropia((LocalDate) caso[0], (LocalDate) caso[1], (Boolean) caso[2]);
            mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
            cuerpo404(get("/api/centros/{id}", centro), EMAIL_INQUILINO);
            cuerpo404(get("/api/centros/{id}/locales", centro), EMAIL_INQUILINO);
            cuerpo404(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
        }
    }

    @Test
    void a2b_14_ultimoDiaDeOcupacionEsInclusivo() throws Exception {
        fijarOcupacionPropia(null, HOY, true);
        Long centro = idCentroDe(LOCAL_PROPIO);
        String token = bearer(EMAIL_INQUILINO);
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/centros/{id}", centro).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(get("/api/centros/{id}/locales", centro).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk());
    }

    // ---- Estado de cuenta y rol leidos de BD ----

    @Test
    void a2b_15_inquilinoDesactivadoNoVeNada() throws Exception {
        String token = bearer(EMAIL_INQUILINO);
        jdbc.update("UPDATE facilityhub.inquilinos SET activo = false WHERE id = ?", idInquilinoDelUsuario(EMAIL_INQUILINO));
        sincronizar();
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/centros/{id}", idCentroDe(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isNotFound());
    }

    @Test
    void a2b_16_usuarioDesactivadoConTokenValidoRecibe403() throws Exception {
        String token = bearer(EMAIL_INQUILINO);
        jdbc.update("UPDATE facilityhub.usuarios SET activo = false WHERE email = ?", EMAIL_INQUILINO);
        sincronizar();
        // Criterio vigente de FH-005A1 (decision D3 pendiente).
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("Operacion no permitida"));
        mvc.perform(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden());
    }

    @Test
    void a2b_17_rolDegradadoEnBdPierdeElAccesoGlobal() throws Exception {
        String tokenExplotacion = bearer(EMAIL_EXPLOTACION);
        jdbc.update("""
            UPDATE facilityhub.usuarios SET rol_id = (SELECT id FROM facilityhub.roles WHERE codigo = 'INQUILINO')
            WHERE email = ?""", EMAIL_EXPLOTACION);
        sincronizar();
        // Sin inquilino asociado: ningun centro y 404 en recursos concretos, aunque el JWT diga EXPLOTACION.
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, tokenExplotacion))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/centros/{id}/locales", idCentroDe(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, tokenExplotacion))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_AJENO)).header(HttpHeaders.AUTHORIZATION, tokenExplotacion))
            .andExpect(status().isNotFound());
    }

    // ---- Filtro JWT ----

    @Test
    void a2b_18_sinTokenOTokenInvalidoDevuelve401() throws Exception {
        Long centro = idCentroDe(LOCAL_PROPIO);
        Long local = idLocal(LOCAL_PROPIO);
        mvc.perform(get("/api/centros")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/centros/{id}", centro)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/centros/{id}/locales", centro)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/locales/{id}/instalaciones", local)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, bearerFirmaAjena(EMAIL_ADMIN, "ADMIN")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, bearerCaducado(EMAIL_ADMIN, "ADMIN")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un-jwt"))
            .andExpect(status().isUnauthorized());
    }

    // ---- FH-005A2-B1: local o centro inactivo ----

    @Test
    void b1_11_localPropioInactivoNoExponeInstalacionesNiApareceEnListados() throws Exception {
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE codigo = ?", LOCAL_PROPIO);
        sincronizar();
        cuerpo404(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
        // Sin otro local activo propio, el centro deja de ser visible y la lista queda vacia.
        mvc.perform(get("/api/centros").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        cuerpo404(get("/api/centros/{id}/locales", idCentroDe(LOCAL_PROPIO)), EMAIL_INQUILINO);
    }

    @Test
    void b1_12_localInactivoSeExcluyeAunqueElCentroSigaVisible() throws Exception {
        Long centro = idCentroDe(LOCAL_PROPIO);
        Long otro = crearLocal(centro, true);
        ocupar(otro, idInquilinoDelUsuario(EMAIL_INQUILINO), HOY.minusDays(10), null, true);
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE codigo = ?", LOCAL_PROPIO);
        sincronizar();
        mvc.perform(get("/api/centros/{id}/locales", centro).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(otro))
            .andExpect(jsonPath("$[*].codigo", not(hasItem(LOCAL_PROPIO))));
    }

    @Test
    void b1_13_centroInactivoConLocalActivoNoExponeInstalaciones() throws Exception {
        jdbc.update("UPDATE facilityhub.centros SET activo = false WHERE id = ?", idCentroDe(LOCAL_PROPIO));
        sincronizar();
        cuerpo404(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
    }

    @Test
    void b1_14_gestionConservaInstalacionesDeLocalYCentroInactivos() throws Exception {
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE codigo = ?", LOCAL_PROPIO);
        jdbc.update("UPDATE facilityhub.centros SET activo = false WHERE id = ?", idCentroDe(LOCAL_PROPIO));
        sincronizar();
        for (String email : new String[] {EMAIL_ADMIN, EMAIL_EXPLOTACION}) {
            mvc.perform(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, bearer(email)))
                .andExpect(status().isOk());
        }
    }

    @Test
    void b1_15_diaSiguienteAlFinDeOcupacionDenegado() throws Exception {
        fijarOcupacionPropia(null, HOY.minusDays(1), true);
        cuerpo404(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
        cuerpo404(get("/api/locales/{id}/inquilino-actual", idLocal(LOCAL_PROPIO)), EMAIL_INQUILINO);
    }

    // ---- Apoyo ----

    // 404 uniforme: mismo titulo/detalle y sin datos de contacto; devuelve el cuerpo para comprobaciones extra.
    private String cuerpo404(MockHttpServletRequestBuilder peticion, String email) throws Exception {
        ResultActions resultado = mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, bearer(email)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Recurso no encontrado"));
        String cuerpo = resultado.andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("cif", "razonSocial", "emailContacto", "telefono", "@", "codigo", "ubicacion");
        return cuerpo;
    }

    private Long idCentroDe(String codigoLocal) {
        return jdbc.queryForObject("SELECT centro_id FROM facilityhub.locales WHERE codigo = ?", Long.class, codigoLocal);
    }

    private Long crearCentro(boolean activo) {
        String codigo = nombreSintetico("c");
        return jdbc.queryForObject("INSERT INTO facilityhub.centros (codigo, nombre, activo) VALUES (?, ?, ?) RETURNING id",
            Long.class, codigo, codigo, activo);
    }

    private Long crearLocal(Long centroId, boolean activo) {
        return jdbc.queryForObject("INSERT INTO facilityhub.locales (centro_id, codigo, activo) VALUES (?, ?, ?) RETURNING id",
            Long.class, centroId, nombreSintetico("l"), activo);
    }

    private Long crearInquilino() {
        return jdbc.queryForObject("INSERT INTO facilityhub.inquilinos (razon_social) VALUES (?) RETURNING id",
            Long.class, nombreSintetico("inq"));
    }

    private void ocupar(Long localId, Long inquilinoId, LocalDate inicio, LocalDate fin, boolean activo) {
        jdbc.update("INSERT INTO facilityhub.ocupaciones_local (local_id, inquilino_id, fecha_inicio, fecha_fin, activo) VALUES (?, ?, ?, ?, ?)",
            localId, inquilinoId, inicio, fin, activo);
    }

    // Solo la ocupacion demo del local propio, dentro de la transaccion de la prueba (se revierte).
    // inicio nulo = conservar la fecha de inicio demo; cada caso fija las tres columnas.
    private LocalDate inicioOriginal;

    private void fijarOcupacionPropia(LocalDate inicio, LocalDate fin, boolean activo) {
        Long local = idLocal(LOCAL_PROPIO);
        Long inquilino = idInquilinoDelUsuario(EMAIL_INQUILINO);
        if (inicioOriginal == null) {
            inicioOriginal = jdbc.queryForObject(
                "SELECT fecha_inicio FROM facilityhub.ocupaciones_local WHERE local_id = ? AND inquilino_id = ?",
                LocalDate.class, local, inquilino);
        }
        // Primero fecha_fin a NULL para no violar ck_ocupacion_fechas al mover fecha_inicio.
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET fecha_fin = NULL WHERE local_id = ? AND inquilino_id = ?", local, inquilino);
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET fecha_inicio = ?, fecha_fin = ?, activo = ? WHERE local_id = ? AND inquilino_id = ?",
            inicio == null ? inicioOriginal : inicio, fin, activo, local, inquilino);
        sincronizar();
    }
}
