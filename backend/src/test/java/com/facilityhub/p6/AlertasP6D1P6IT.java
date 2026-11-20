package com.facilityhub.p6;

import com.facilityhub.p4.P4IntegracionBase;
import com.facilityhub.p4.P4RelojConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-006 / P6-D1 - Alertas internas por eventos con MockMvc, JWT y JPQL reales sobre facilityhub_fh005_p3 (barrera P4 sin
 * cambios, rollback por prueba). Requiere V8 aplicada: si falta, aborta. Datos sinteticos con prefijo P4- revertidos.
 */
class AlertasP6D1P6IT extends P4IntegracionBase {
    private static final LocalDate HOY = P4RelojConfig.HOY_MADRID;

    @BeforeEach
    void exigirV8() {
        Integer v8 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.flyway_schema_history WHERE version = '8' AND success", Integer.class);
        if (v8 == null || v8 != 1) {
            throw new IllegalStateException("P6-D1 abortada: la base de pruebas no tiene V8 aplicada una vez");
        }
    }

    @Test
    void p6d1_it_01_circuitoNhoodGeneraLasAlertasEsperadas() throws Exception {
        Long proveedor = insertarProveedor();
        Long tecnicoId = insertarTecnico(proveedor);
        String tecnico = email(tecnicoId);
        Long mantenimiento = nuevoMantenimiento(obligacion("NHOOD"), HOY.plusDays(20));
        sincronizar();

        mvc.perform(json(put("/api/mantenimientos/{id}/asignacion", mantenimiento), EMAIL_EXPLOTACION, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isOk());
        sincronizar();
        assertThat(alertas(tecnicoId, "ASIGNACION")).isEqualTo(1);

        Long id = idDe(mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico,
                "{\"fechaEjecucion\":\"" + HOY.minusDays(1) + "\",\"actuaciones\":\"P4- revision\",\"resultado\":\"CONFORME\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        documento(id, mantenimiento);
        sincronizar();
        mvc.perform(json(post("/api/intervenciones/{id}/envio", id), tecnico, "{\"version\":" + version(id) + "}")).andExpect(status().isOk());
        sincronizar();
        Long explotacion = idUsuario(EMAIL_EXPLOTACION);
        assertThat(alertas(explotacion, "EVIDENCIAS_PRESENTADAS")).isEqualTo(1);
        // Doble envio (409): no se duplica la alerta.
        mvc.perform(json(post("/api/intervenciones/{id}/envio", id), tecnico, "{\"version\":" + version(id) + "}")).andExpect(status().isConflict());
        assertThat(alertas(explotacion, "EVIDENCIAS_PRESENTADAS")).isEqualTo(1);

        mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), EMAIL_EXPLOTACION,
            "{\"accion\":\"SOLICITAR_CORRECCION\",\"comentario\":\"P4- falta firma\",\"version\":" + version(id) + "}")).andExpect(status().isOk());
        sincronizar();
        assertThat(alertas(tecnicoId, "CORRECCION_SOLICITADA")).isEqualTo(1);
        mvc.perform(json(post("/api/intervenciones/{id}/envio", id), tecnico, "{\"version\":" + version(id) + "}")).andExpect(status().isOk());
        sincronizar();
        assertThat(alertas(explotacion, "EVIDENCIAS_PRESENTADAS")).isEqualTo(2); // un aviso por envio

        mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), EMAIL_EXPLOTACION,
            "{\"accion\":\"APROBAR\",\"version\":" + version(id) + "}")).andExpect(status().isOk());
        sincronizar();
        assertThat(alertas(tecnicoId, "APROBADO")).isEqualTo(1);
        // Obligacion NHOOD de su local, cubierta por su ocupacion vigente: el inquilino tambien recibe la aprobacion.
        assertThat(alertas(idUsuario(EMAIL_INQUILINO), "APROBADO")).isEqualTo(1);
        // El autor no se avisa a si mismo.
        assertThat(alertas(explotacion, "APROBADO") + alertas(explotacion, "CORRECCION_SOLICITADA")).isZero();
    }

    @Test
    void p6d1_it_02_declaracionDelInquilinoAvisaAExplotacion() throws Exception {
        Long obligacion = obligacion("NHOOD");
        jdbc.update("UPDATE facilityhub.obligaciones_mantenimiento SET responsable = 'INQUILINO' WHERE id = ?", obligacion);
        Long proveedor = insertarProveedor();
        sincronizar();
        mvc.perform(json(put("/api/inquilino/obligaciones/{id}/proveedor", obligacion), EMAIL_INQUILINO, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isOk());
        sincronizar();
        assertThat(alertas(idUsuario(EMAIL_EXPLOTACION), "PROVEEDOR_DECLARADO")).isEqualTo(1);
        assertThat(alertas(idUsuario(EMAIL_INQUILINO), "PROVEEDOR_DECLARADO")).isZero();
        // Redeclarar el mismo proveedor (idempotente) no genera otra alerta.
        mvc.perform(json(put("/api/inquilino/obligaciones/{id}/proveedor", obligacion), EMAIL_INQUILINO, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isOk());
        assertThat(alertas(idUsuario(EMAIL_EXPLOTACION), "PROVEEDOR_DECLARADO")).isEqualTo(1);
    }

    @Test
    void p6d1_it_03_cadaUsuarioSoloConsultaYMarcaSusAlertas() throws Exception {
        Long proveedor = insertarProveedor();
        Long tecnicoId = insertarTecnico(proveedor);
        String tecnico = email(tecnicoId);
        Long mantenimiento = nuevoMantenimiento(obligacion("NHOOD"), HOY.plusDays(20));
        sincronizar();
        mvc.perform(json(put("/api/mantenimientos/{id}/asignacion", mantenimiento), EMAIL_EXPLOTACION, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isOk());
        sincronizar();
        Long alerta = jdbc.queryForObject("SELECT id FROM facilityhub.alertas WHERE destinatario_usuario_id = ?", Long.class, tecnicoId);

        mvc.perform(get("/api/alertas").header(HttpHeaders.AUTHORIZATION, bearer(tecnico)))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].tipo", is("ASIGNACION")));
        mvc.perform(get("/api/alertas/no-leidas").header(HttpHeaders.AUTHORIZATION, bearer(tecnico)))
            .andExpect(jsonPath("$.noLeidas", is(1)));
        // Otro usuario no ve ni marca la alerta ajena (404 uniforme).
        mvc.perform(get("/api/alertas").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + alerta + ")]", hasSize(0)));
        mvc.perform(patch("/api/alertas/{id}/leida", alerta).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/alertas/{id}/leida", alerta).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))).andExpect(status().isNotFound());
        // El destinatario la marca como leida (idempotente).
        mvc.perform(patch("/api/alertas/{id}/leida", alerta).header(HttpHeaders.AUTHORIZATION, bearer(tecnico)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.leidaAt").exists());
        mvc.perform(patch("/api/alertas/{id}/leida", alerta).header(HttpHeaders.AUTHORIZATION, bearer(tecnico))).andExpect(status().isOk());
        sincronizar();
        mvc.perform(get("/api/alertas/no-leidas").header(HttpHeaders.AUTHORIZATION, bearer(tecnico))).andExpect(jsonPath("$.noLeidas", is(0)));
        mvc.perform(get("/api/alertas")).andExpect(status().isUnauthorized());
    }

    @Test
    void p6d1_it_04_claveUnicaPorDestinatarioTipoYEvento() {
        Long usuario = idUsuario(EMAIL_ADMIN);
        String sql = "INSERT INTO facilityhub.alertas (destinatario_usuario_id, tipo, evento_ref, mensaje) VALUES (?, 'ASIGNACION', 'P4-evento', 'P4- prueba') "
            + "ON CONFLICT (destinatario_usuario_id, tipo, evento_ref) DO NOTHING";
        assertThat(jdbc.update(sql, usuario)).isEqualTo(1);
        assertThat(jdbc.update(sql, usuario)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.alertas WHERE evento_ref = 'P4-evento'", Integer.class)).isEqualTo(1);
    }

    // ---- Utilidades (datos sinteticos, revertidos al final de cada prueba) ----

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String email, String cuerpo) {
        return b.header(HttpHeaders.AUTHORIZATION, bearer(email)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static Long idDe(String cuerpo) {
        return Long.valueOf(cuerpo.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private int alertas(Long usuario, String tipo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.alertas WHERE destinatario_usuario_id = ? AND tipo = ?",
            Integer.class, usuario, tipo);
    }

    private long version(Long intervencion) {
        return jdbc.queryForObject("SELECT version FROM facilityhub.intervenciones WHERE id = ?", Long.class, intervencion);
    }

    private Long idUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM facilityhub.usuarios WHERE email = ?", Long.class, email);
    }

    private String email(Long usuarioId) {
        return jdbc.queryForObject("SELECT email FROM facilityhub.usuarios WHERE id = ?", String.class, usuarioId);
    }

    private Long obligacion(String responsable) {
        return jdbc.queryForObject("""
            SELECT MIN(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.responsable = ? AND o.fecha_inicio <= ?""", Long.class, LOCAL_PROPIO, responsable, HOY.minusDays(30));
    }

    private Long nuevoMantenimiento(Long obligacion, LocalDate fecha) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.mantenimientos (obligacion_id, fecha_programada, descripcion, validacion_requerida)
            VALUES (?, ?, 'P4- mantenimiento sintetico', TRUE) RETURNING id""", Long.class, obligacion, fecha);
    }

    private void documento(Long intervencion, Long mantenimiento) {
        jdbc.update("""
            INSERT INTO facilityhub.documentos_mantenimiento (mantenimiento_id, nombre_original, ruta_archivo, estado_revision,
              visibilidad, intervencion_id, categoria) VALUES (?, 'P4-sintetico.pdf', 'P4-sin-archivo', 'PENDIENTE', 'INTERNO', ?, 'EVIDENCIA')""",
            mantenimiento, intervencion);
    }

    private Long insertarProveedor() {
        return jdbc.queryForObject("INSERT INTO facilityhub.proveedores (razon_social, activo) VALUES (?, TRUE) RETURNING id",
            Long.class, nombreSintetico("proveedor"));
    }

    private Long insertarTecnico(Long proveedorId) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.usuarios (nombre, email, password_hash, rol_id, proveedor_id)
            VALUES ('P4-tecnico', ?, 'x', (SELECT id FROM facilityhub.roles WHERE codigo = 'PROVEEDOR'), ?) RETURNING id""",
            Long.class, nombreSintetico("tec").toLowerCase() + "@sintetico.test", proveedorId);
    }
}
