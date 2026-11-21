package com.facilityhub.p6;

import com.facilityhub.config.RecordatoriosProgramados;
import com.facilityhub.p4.P4IntegracionBase;
import com.facilityhub.p4.P4RelojConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-006 / P6-D2 - Recordatorios de vencimiento con MockMvc, JWT y JPQL reales sobre facilityhub_fh005_p3 (barrera P4 sin
 * cambios, rollback por prueba). Requiere V9 aplicada: si falta, aborta. Datos sinteticos con prefijo P4- revertidos.
 * Las comprobaciones se limitan a los mantenimientos sinteticos de cada prueba.
 */
class RecordatoriosP6D2P6IT extends P4IntegracionBase {
    private static final LocalDate HOY = P4RelojConfig.HOY_MADRID;
    private static final String EJECUTAR = "/api/admin/recordatorios/ejecutar";

    @Autowired
    private ApplicationContext contexto;

    @BeforeEach
    void exigirV9() {
        Integer v9 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.flyway_schema_history WHERE version = '9' AND success", Integer.class);
        if (v9 == null || v9 != 1) {
            throw new IllegalStateException("P6-D2 abortada: la base de pruebas no tiene V9 aplicada una vez");
        }
    }

    @Test
    void p6d2_it_01_nhoodProximoYVencidoSinDuplicadosYAuditado() throws Exception {
        Long proveedor = insertarProveedor();
        Long tecnico = insertarTecnico(proveedor);
        Long obligacion = obligacion("NHOOD");
        Long proximo = nuevoMantenimiento(obligacion, HOY.plusDays(10));
        Long vencido = nuevoMantenimiento(obligacion, HOY.minusDays(3));
        sincronizar();
        mvc.perform(json(put("/api/mantenimientos/{id}/asignacion", proximo), EMAIL_EXPLOTACION, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isOk());
        sincronizar();
        Long explotacion = idUsuario(EMAIL_EXPLOTACION);
        int auditoriasAntes = auditorias();

        mvc.perform(post(EJECUTAR).param("simulacion", "false").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.simulacion", is(false)));
        sincronizar();
        assertThat(alertas(tecnico, "VENCIMIENTO_PROXIMO", proximo)).isEqualTo(1);
        assertThat(alertas(explotacion, "VENCIMIENTO_PROXIMO", proximo)).isEqualTo(1);
        assertThat(alertas(explotacion, "MANTENIMIENTO_VENCIDO", vencido)).isEqualTo(1);
        assertThat(alertas(tecnico, "MANTENIMIENTO_VENCIDO", vencido)).isZero(); // sin asignacion en ese mantenimiento
        // NHOOD: el inquilino no recibe recordatorios; ADMIN tampoco.
        assertThat(alertasDe(idUsuario(EMAIL_INQUILINO), proximo, vencido) + alertasDe(idUsuario(EMAIL_ADMIN), proximo, vencido)).isZero();
        assertThat(auditorias()).isEqualTo(auditoriasAntes + 1);

        // Segunda ejecucion el mismo dia: ningun aviso nuevo.
        int total = alertasDe(null, proximo, vencido);
        mvc.perform(post(EJECUTAR).param("simulacion", "false").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isOk());
        sincronizar();
        assertThat(alertasDe(null, proximo, vencido)).isEqualTo(total);
    }

    @Test
    void p6d2_it_02_simulacionPorDefectoSinEscriturasYSoloAdmin() throws Exception {
        nuevoMantenimiento(obligacion("NHOOD"), HOY.minusDays(2));
        sincronizar();
        int antes = jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.alertas", Integer.class);
        int auditoriasAntes = auditorias();
        mvc.perform(post(EJECUTAR).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.simulacion", is(true)));
        sincronizar();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.alertas", Integer.class)).isEqualTo(antes);
        assertThat(auditorias()).isEqualTo(auditoriasAntes);
        for (String email : new String[] {EMAIL_EXPLOTACION, EMAIL_INQUILINO}) {
            mvc.perform(post(EJECUTAR).header(HttpHeaders.AUTHORIZATION, bearer(email))).andExpect(status().isForbidden());
        }
        mvc.perform(post(EJECUTAR)).andExpect(status().isUnauthorized());
    }

    @Test
    void p6d2_it_03_exclusionesCanceladoFinalizadoPausadaYAntiguo() throws Exception {
        Long obligacion = obligacion("NHOOD");
        Long cancelado = nuevoMantenimiento(obligacion, HOY.minusDays(2));
        jdbc.update("UPDATE facilityhub.mantenimientos SET estado = 'CANCELADO' WHERE id = ?", cancelado);
        Long finalizado = nuevoMantenimiento(obligacion, HOY.minusDays(2));
        jdbc.update("UPDATE facilityhub.mantenimientos SET estado = 'FINALIZADO', fecha_realizacion = ? WHERE id = ?", HOY.minusDays(2), finalizado);
        Long antiguo = nuevoMantenimiento(obligacion, HOY.minusDays(95)); // mas alla de la antiguedad maxima (90)
        Long pausado = nuevoMantenimiento(obligacion, HOY.minusDays(2));
        sincronizar();
        ejecutarReal();
        assertThat(alertasDe(null, cancelado, finalizado, antiguo)).isZero();
        assertThat(alertasDe(null, pausado)).isPositive();
        // Obligacion en pausa: no se generan avisos para un mantenimiento nuevo de esa obligacion.
        jdbc.update("UPDATE facilityhub.obligaciones_mantenimiento SET estado = 'PAUSADA' WHERE id = ?", obligacion);
        Long otro = nuevoMantenimiento(obligacion, HOY.minusDays(1));
        sincronizar();
        ejecutarReal();
        assertThat(alertasDe(null, otro)).isZero();
    }

    @Test
    void p6d2_it_04_inquilinoAislamientoYLecturaSinCambiarElMantenimiento() throws Exception {
        Long obligacion = obligacion("NHOOD");
        jdbc.update("UPDATE facilityhub.obligaciones_mantenimiento SET responsable = 'INQUILINO' WHERE id = ?", obligacion);
        Long proveedor = insertarProveedor();
        Long tecnico = insertarTecnico(proveedor);
        Long ajeno = insertarTecnico(insertarProveedor());
        Long vencido = nuevoMantenimiento(obligacion, HOY.minusDays(5));
        // En obligaciones INQUILINO la gestion solo asigna el proveedor aceptado del inquilino (regla P6-C2): la asignacion
        // activa se crea directamente (revertida al final) para probar solo los destinatarios del recordatorio.
        jdbc.update("""
            INSERT INTO facilityhub.asignaciones_mantenimiento (mantenimiento_id, proveedor_id, asignado_por_usuario_id)
            VALUES (?, ?, ?)""", vencido, proveedor, idUsuario(EMAIL_EXPLOTACION));
        sincronizar();
        ejecutarReal();
        Long inquilino = idUsuario(EMAIL_INQUILINO);
        assertThat(alertas(inquilino, "MANTENIMIENTO_VENCIDO", vencido)).isEqualTo(1);
        assertThat(alertas(tecnico, "MANTENIMIENTO_VENCIDO", vencido)).isEqualTo(1);
        assertThat(alertas(idUsuario(EMAIL_EXPLOTACION), "MANTENIMIENTO_VENCIDO", vencido)).isEqualTo(1);
        assertThat(alertasDe(ajeno, vencido)).isZero();

        // Marcar como leida no altera el mantenimiento.
        String antes = jdbc.queryForObject("SELECT estado || ':' || version FROM facilityhub.mantenimientos WHERE id = ?", String.class, vencido);
        Long alerta = jdbc.queryForObject("SELECT id FROM facilityhub.alertas WHERE destinatario_usuario_id = ? AND mantenimiento_id = ? AND tipo = 'MANTENIMIENTO_VENCIDO'",
            Long.class, inquilino, vencido);
        mvc.perform(patch("/api/alertas/{id}/leida", alerta).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))).andExpect(status().isOk());
        mvc.perform(patch("/api/alertas/{id}/leida", alerta).header(HttpHeaders.AUTHORIZATION, bearer(email(ajeno)))).andExpect(status().isNotFound());
        sincronizar();
        assertThat(jdbc.queryForObject("SELECT estado || ':' || version FROM facilityhub.mantenimientos WHERE id = ?", String.class, vencido)).isEqualTo(antes);
    }

    @Test
    void p6d2_it_05_tareaProgramadaDesactivadaPorDefecto() {
        assertThat(contexto.getBeanNamesForType(RecordatoriosProgramados.class)).isEmpty();
        assertThat(contexto.getEnvironment().getProperty("facilityhub.recordatorios.enabled")).isEqualTo("false");
    }

    // ---- Utilidades (datos sinteticos, revertidos al final de cada prueba) ----

    private void ejecutarReal() throws Exception {
        mvc.perform(post(EJECUTAR).param("simulacion", "false").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isOk());
        sincronizar();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String email, String cuerpo) {
        return b.header(HttpHeaders.AUTHORIZATION, bearer(email)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private int alertas(Long usuario, String tipo, Long mantenimiento) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.alertas WHERE destinatario_usuario_id = ? AND tipo = ? AND mantenimiento_id = ?",
            Integer.class, usuario, tipo, mantenimiento);
    }

    // Alertas de cualquier tipo de recordatorio sobre los mantenimientos indicados (usuario null = todos).
    private int alertasDe(Long usuario, Long... mantenimientos) {
        int n = 0;
        for (Long m : mantenimientos) {
            n += jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.alertas WHERE mantenimiento_id = ? "
                + "AND tipo IN ('VENCIMIENTO_PROXIMO', 'MANTENIMIENTO_VENCIDO', 'VALIDACION_PENDIENTE_VENCIDA') "
                + "AND (CAST(? AS BIGINT) IS NULL OR destinatario_usuario_id = ?)", Integer.class, m, usuario, usuario);
        }
        return n;
    }

    private int auditorias() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.auditoria_eventos WHERE entidad = 'RECORDATORIOS'", Integer.class);
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
            WHERE l.codigo = ? AND o.responsable = ? AND o.fecha_inicio <= ?""", Long.class, LOCAL_PROPIO, responsable, HOY.minusDays(100));
    }

    private Long nuevoMantenimiento(Long obligacion, LocalDate fecha) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.mantenimientos (obligacion_id, fecha_programada, descripcion, validacion_requerida)
            VALUES (?, ?, 'P4- mantenimiento sintetico', TRUE) RETURNING id""", Long.class, obligacion, fecha);
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
