package com.facilityhub.p6;

import com.facilityhub.p4.P4IntegracionBase;
import com.facilityhub.p4.P4RelojConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-006 / P6-D3-2 - Historial del local con MockMvc, JWT y JPQL reales sobre facilityhub_fh005_p3 (barrera P4 sin cambios,
 * rollback por prueba). Requiere V9. Datos sinteticos con prefijo P4- en una obligacion sintetica NHOOD de L-101 (zona del
 * inquilino demo, ocupacion desde 2026-01-01). Las comprobaciones se limitan a los mantenimientos sinteticos.
 */
class HistorialP6D3P6IT extends P4IntegracionBase {
    private static final LocalDate HOY = P4RelojConfig.HOY_MADRID;
    private static final LocalDate ANTES_DE_LA_OCUPACION = LocalDate.of(2025, 6, 1);

    @BeforeEach
    void exigirV9() {
        Integer v9 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.flyway_schema_history WHERE version = '9' AND success", Integer.class);
        if (v9 == null || v9 != 1) {
            throw new IllegalStateException("P6-D3 abortada: la base de pruebas no tiene V9 aplicada una vez");
        }
    }

    @Test
    void p6d3_it_10_historialGestionEInquilinoConAislamiento() throws Exception {
        Escenario e = escenario();
        String local = "/api/locales/" + idLocal(LOCAL_PROPIO) + "/historial";

        // Gestion: aprobados (propio y de la ocupacion anterior), cierre sin cumplimiento e historico P5; no el pendiente.
        for (String email : new String[] {EMAIL_EXPLOTACION, EMAIL_ADMIN}) {
            List<Map<String, Object>> h = filas(leer(local, email));
            assertThat(tipo(h, e.propio())).isEqualTo("APROBADO");
            assertThat(tipo(h, e.anterior())).isEqualTo("APROBADO");
            assertThat(tipo(h, e.cerrado())).isEqualTo("CERRADO_SIN_CUMPLIMIENTO");
            Map<String, Object> p5 = fila(h, e.p5());
            assertThat(p5.get("tipoRegistro")).isEqualTo("HISTORICO_P5");
            assertThat(p5.get("etiqueta")).isEqualTo("Histórico P5 — sin validación");
            assertThat(p5.get("estadoValidacion")).isNull();
            assertThat(p5.get("intervencionId")).isNull();
            assertThat(p5.get("estadoMantenimiento")).isEqualTo("FINALIZADO");
            assertThat(ids(h)).doesNotContain(e.pendiente());
            // Gestion ve tambien los documentos internos de la intervencion aprobada.
            assertThat(((List<?>) fila(h, e.propio()).get("documentos"))).hasSize(3);
        }

        // INQUILINO: solo el aprobado de su ocupacion, solo el certificado compartido, sin datos internos.
        List<Map<String, Object>> hi = filas(leer(local, EMAIL_INQUILINO));
        assertThat(ids(hi)).contains(e.propio())
            .doesNotContain(e.anterior(), e.cerrado(), e.p5(), e.pendiente());
        Map<String, Object> propio = fila(hi, e.propio());
        assertThat(propio).doesNotContainKeys("observaciones", "documentos", "tipoRegistro", "proveedor");
        List<Map<String, Object>> certificados = JsonPath.read(propio, "$.cumplimiento.certificados");
        assertThat(certificados).extracting(c -> ((Number) c.get("id")).longValue()).containsExactly(e.certificadoCompartido());
    }

    @Test
    void p6d3_it_11_accesoLocalAjenoProveedorYSinEscrituras() throws Exception {
        escenario();
        Long ajeno = idLocal(LOCAL_AJENO);
        mvc.perform(get("/api/locales/{id}/historial", ajeno).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/locales/{id}/historial", 999_999_999L).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/locales/{id}/historial", 999_999_999L).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/locales/{id}/historial", ajeno).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isOk());
        String tecnico = email(insertarTecnico(insertarProveedor()));
        sincronizar();
        mvc.perform(get("/api/locales/{id}/historial", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, bearer(tecnico)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/locales/{id}/historial", idLocal(LOCAL_PROPIO))).andExpect(status().isUnauthorized());

        String antes = huella();
        for (String email : new String[] {EMAIL_EXPLOTACION, EMAIL_ADMIN, EMAIL_INQUILINO}) {
            leer("/api/locales/" + idLocal(LOCAL_PROPIO) + "/historial", email);
        }
        sincronizar();
        assertThat(huella()).isEqualTo(antes);
    }

    // ---- Escenario sintetico (revertido al final de cada prueba) ----

    private record Escenario(Long propio, Long anterior, Long cerrado, Long pendiente, Long p5, Long certificadoCompartido) {}

    private Escenario escenario() throws Exception {
        Long obligacion = jdbc.queryForObject("""
            INSERT INTO facilityhub.obligaciones_mantenimiento (local_id, tipo_mantenimiento_id, fecha_inicio, proxima_fecha,
              responsable, observaciones)
            SELECT l.id, (SELECT id FROM facilityhub.tipos_mantenimiento WHERE codigo = 'BT-ANUAL'), DATE '2025-01-01', ?, 'NHOOD',
              'P4- obligacion historial' FROM facilityhub.locales l WHERE l.codigo = ? RETURNING id""",
            Long.class, HOY.plusDays(30), LOCAL_PROPIO);
        Long proveedor = insertarProveedor();
        String tecnico = email(insertarTecnico(proveedor));
        Long anterior = nuevoMantenimiento(obligacion, ANTES_DE_LA_OCUPACION);
        Long propio = nuevoMantenimiento(obligacion, HOY.minusDays(10));
        Long cerrado = nuevoMantenimiento(obligacion, HOY.minusDays(5));
        Long pendiente = nuevoMantenimiento(obligacion, HOY.minusDays(3));
        Long p5 = jdbc.queryForObject("""
            INSERT INTO facilityhub.mantenimientos (obligacion_id, fecha_programada, fecha_realizacion, estado, descripcion,
              observaciones, validacion_requerida)
            VALUES (?, DATE '2026-03-01', DATE '2026-03-01', 'FINALIZADO', 'P4- historico P5', 'P4- nota interna', FALSE)
            RETURNING id""", Long.class, obligacion);
        // Documento P5 compartido y valido: ni asi debe verlo el inquilino en el historial (no es un cumplimiento P6).
        insertarDocumento(p5, "P4-legado.pdf", "VALIDO", "COMPARTIDO", "P4-sin-archivo");
        sincronizar();

        // Orden cronologico: cada ejecucion debe ser posterior a la ultima aprobada de la obligacion.
        Long iAnterior = registrar(anterior, proveedor, tecnico, "CONFORME");
        Long certAnterior = documento(anterior, iAnterior, "CERTIFICADO");
        enviar(iAnterior, tecnico);
        validar(iAnterior, "APROBAR", List.of(certAnterior));

        Long iPropio = registrar(propio, proveedor, tecnico, "CONFORME");
        Long certCompartido = documento(propio, iPropio, "CERTIFICADO");
        documento(propio, iPropio, "CERTIFICADO");
        documento(propio, iPropio, "EVIDENCIA");
        enviar(iPropio, tecnico);
        validar(iPropio, "APROBAR", List.of(certCompartido));

        Long iCerrado = registrar(cerrado, proveedor, tecnico, "NO_REALIZADO");
        documento(cerrado, iCerrado, "EVIDENCIA");
        enviar(iCerrado, tecnico);
        validar(iCerrado, "CERRAR_SIN_CUMPLIMIENTO", List.of());

        Long iPendiente = registrar(pendiente, proveedor, tecnico, "CONFORME");
        documento(pendiente, iPendiente, "EVIDENCIA");
        enviar(iPendiente, tecnico);
        return new Escenario(propio, anterior, cerrado, pendiente, p5, certCompartido);
    }

    private Long registrar(Long mantenimiento, Long proveedor, String tecnico, String resultado) throws Exception {
        mvc.perform(json(put("/api/mantenimientos/{id}/asignacion", mantenimiento), EMAIL_EXPLOTACION, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isOk());
        String fecha = jdbc.queryForObject("SELECT fecha_programada::text FROM facilityhub.mantenimientos WHERE id = ?", String.class, mantenimiento);
        String alta = mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico,
                "{\"fechaEjecucion\":\"" + fecha + "\",\"actuaciones\":\"P4- revision\",\"resultado\":\"" + resultado + "\""
                    + ("CONFORME".equals(resultado) ? "" : ",\"incidencias\":\"P4- no se pudo acceder\"") + "}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        sincronizar();
        return ((Number) JsonPath.read(alta, "$.id")).longValue();
    }

    private Long documento(Long mantenimiento, Long intervencion, String categoria) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.documentos_mantenimiento (mantenimiento_id, nombre_original, ruta_archivo, estado_revision,
              visibilidad, intervencion_id, categoria) VALUES (?, 'P4-sintetico.pdf', 'P4-sin-archivo', 'PENDIENTE', 'INTERNO', ?, ?)
            RETURNING id""", Long.class, mantenimiento, intervencion, categoria);
    }

    private void enviar(Long intervencion, String tecnico) throws Exception {
        sincronizar();
        mvc.perform(json(post("/api/intervenciones/{id}/envio", intervencion), tecnico, "{\"version\":" + version(intervencion) + "}"))
            .andExpect(status().isOk());
        sincronizar();
    }

    private void validar(Long intervencion, String accion, List<Long> compartidos) throws Exception {
        mvc.perform(json(post("/api/intervenciones/{id}/validacion", intervencion), EMAIL_EXPLOTACION,
                "{\"accion\":\"" + accion + "\",\"comentario\":\"P4- revision\",\"version\":" + version(intervencion)
                    + ",\"documentosCompartidos\":" + compartidos + "}"))
            .andExpect(status().isOk());
        sincronizar();
    }

    // ---- Utilidades ----

    private String huella() {
        return jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) || ':' || COALESCE(SUM(version), 0) || ':' || COALESCE(MAX(updated_at)::text, '')
                      FROM facilityhub.mantenimientos)
                || '|' || (SELECT COUNT(*) || ':' || COALESCE(SUM(version), 0) FROM facilityhub.intervenciones)
                || '|' || (SELECT COUNT(*) || ':' || COUNT(*) FILTER (WHERE visibilidad = 'COMPARTIDO')
                           || ':' || COUNT(*) FILTER (WHERE estado_revision = 'VALIDO') FROM facilityhub.documentos_mantenimiento)
                || '|' || (SELECT COUNT(*) FROM facilityhub.alertas)
                || '|' || (SELECT COUNT(*) FROM facilityhub.auditoria_eventos)
            """, String.class);
    }

    private String leer(String ruta, String email) throws Exception {
        return mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, bearer(email)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static List<Map<String, Object>> filas(String json) {
        return JsonPath.read(json, "$");
    }

    private static List<Long> ids(List<Map<String, Object>> filas) {
        return filas.stream().map(f -> ((Number) f.get("mantenimientoId")).longValue()).toList();
    }

    private static Map<String, Object> fila(List<Map<String, Object>> filas, Long mantenimiento) {
        return filas.stream().filter(f -> ((Number) f.get("mantenimientoId")).longValue() == mantenimiento).findFirst()
            .orElseThrow(() -> new AssertionError("Mantenimiento " + mantenimiento + " ausente del historial"));
    }

    private static Object tipo(List<Map<String, Object>> filas, Long mantenimiento) {
        return fila(filas, mantenimiento).get("tipoRegistro");
    }

    private Long version(Long intervencion) {
        return jdbc.queryForObject("SELECT version FROM facilityhub.intervenciones WHERE id = ?", Long.class, intervencion);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String email, String cuerpo) {
        return b.header(HttpHeaders.AUTHORIZATION, bearer(email)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private String email(Long usuarioId) {
        return jdbc.queryForObject("SELECT email FROM facilityhub.usuarios WHERE id = ?", String.class, usuarioId);
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
