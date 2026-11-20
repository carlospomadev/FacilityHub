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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-006 / P6-C1 - Intervenciones, evidencias, validacion, auditoria y esquema V6 con MockMvc, JWT y JPQL reales sobre
 * facilityhub_fh005_p3 (barrera P4 sin cambios, rollback por prueba). Requiere V6 aplicada: si falta, aborta.
 * Los documentos de intervencion se insertan por SQL (sin escribir archivos); la subida multipart y la concurrencia entre
 * peticiones simultaneas se validan en el entorno desechable. Datos sinteticos con prefijo P4- revertidos.
 */
class IntervencionesP6C1P6IT extends P4IntegracionBase {
    // Reloj fijo del contexto P4 (P4RelojConfig): "ayer" respecto a ese dia, no al reloj real.
    private static final LocalDate AYER = P4RelojConfig.HOY_MADRID.minusDays(1);

    @BeforeEach
    void exigirV6() {
        Integer v6 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.flyway_schema_history WHERE version = '6' AND success", Integer.class);
        if (v6 == null || v6 != 1) {
            throw new IllegalStateException("P6-C1 abortada: la base de pruebas no tiene V6 aplicada una vez");
        }
    }

    @Test
    void p6c1_it_01_v6ConservaLosDatosPrevios() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.tipos_mantenimiento WHERE regla_recalculo <> 'DESDE_EJECUCION'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.documentos_mantenimiento WHERE intervencion_id IS NOT NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.mantenimientos WHERE validacion_requerida", Integer.class)).isZero();
    }

    @Test
    void p6c1_it_02_flujoCompletoNhoodConCorreccionReenvioYAprobacion() throws Exception {
        Long proveedor = insertarProveedor();
        String tecnico = emailTecnico(insertarTecnico(proveedor));
        Long obligacion = obligacionNhoodPropia();
        Long mantenimiento = nuevoMantenimiento(obligacion);
        insertarAsignacion(mantenimiento, proveedor);
        LocalDate proximaInicial = proxima(obligacion);
        int meses = jdbc.queryForObject("""
            SELECT t.periodicidad_meses FROM facilityhub.tipos_mantenimiento t
            JOIN facilityhub.obligaciones_mantenimiento o ON o.tipo_mantenimiento_id = t.id WHERE o.id = ?""", Integer.class, obligacion);
        sincronizar();

        Long id = idDe(mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico,
                "{\"fechaEjecucion\":\"" + AYER + "\",\"actuaciones\":\"P4- revision\",\"resultado\":\"CONFORME\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.origen", is("PROVEEDOR")))
            .andExpect(jsonPath("$.estadoValidacion", is("BORRADOR")))
            .andReturn().getResponse().getContentAsString());
        Long certificado = insertarDocumentoDeIntervencion(id, mantenimiento, "CERTIFICADO");
        Long evidencia = insertarDocumentoDeIntervencion(id, mantenimiento, "EVIDENCIA");
        sincronizar();

        mvc.perform(json(post("/api/intervenciones/{id}/envio", id), tecnico, "{\"version\":" + version(id) + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.numeroEnvio", is(1)));
        sincronizar();
        assertThat(proxima(obligacion)).isEqualTo(proximaInicial);
        mvc.perform(json(post("/api/intervenciones/{id}/envio", id), tecnico, "{\"version\":" + version(id) + "}"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/intervenciones/pendientes").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isOk());

        mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), EMAIL_EXPLOTACION,
                "{\"accion\":\"SOLICITAR_CORRECCION\",\"comentario\":\"P4- falta firma\",\"version\":" + version(id) + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estadoValidacion", is("REQUIERE_CORRECCION")));
        sincronizar();
        assertThat(estadoMantenimiento(mantenimiento)).isEqualTo("EN_PROCESO");
        assertThat(proxima(obligacion)).isEqualTo(proximaInicial);

        mvc.perform(json(post("/api/intervenciones/{id}/envio", id), tecnico, "{\"version\":" + version(id) + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.numeroEnvio", is(2)));
        sincronizar();
        mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), EMAIL_EXPLOTACION,
                "{\"accion\":\"APROBAR\",\"version\":" + version(id) + ",\"documentosCompartidos\":[" + evidencia + "]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), EMAIL_EXPLOTACION,
                "{\"accion\":\"APROBAR\",\"version\":" + version(id) + ",\"documentosCompartidos\":[" + certificado + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoValidacion", is("APROBADO")))
            .andExpect(jsonPath("$.validaciones", hasSize(2)));
        sincronizar();

        assertThat(proxima(obligacion)).isEqualTo(AYER.plusMonths(meses));
        assertThat(estadoMantenimiento(mantenimiento)).isEqualTo("FINALIZADO");
        assertThat(valorEnBd("estado_revision || '/' || visibilidad", certificado)).isEqualTo("VALIDO/COMPARTIDO");
        assertThat(valorEnBd("estado_revision || '/' || visibilidad", evidencia)).isEqualTo("VALIDO/INTERNO");
        assertThat(jdbc.queryForObject("""
            SELECT string_agg(accion, ',' ORDER BY id) FROM facilityhub.auditoria_eventos
            WHERE entidad = 'INTERVENCION' AND entidad_id = ?""", String.class, id))
            .isEqualTo("REGISTRAR,ENVIAR,SOLICITAR_CORRECCION,ENVIAR,APROBAR");
        mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico, "{}"))
            .andExpect(status().isConflict());
    }

    @Test
    void p6c1_it_03_noRealizadoNoSeApruebaNiFinaliza() throws Exception {
        Long proveedor = insertarProveedor();
        String tecnico = emailTecnico(insertarTecnico(proveedor));
        Long obligacion = obligacionNhoodPropia();
        Long mantenimiento = nuevoMantenimiento(obligacion);
        insertarAsignacion(mantenimiento, proveedor);
        LocalDate proximaInicial = proxima(obligacion);
        sincronizar();
        Long id = idDe(mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico,
                "{\"fechaEjecucion\":\"" + AYER + "\",\"actuaciones\":\"P4- visita\",\"resultado\":\"NO_REALIZADO\",\"incidencias\":\"P4- local cerrado\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        sincronizar();
        mvc.perform(json(post("/api/intervenciones/{id}/envio", id), tecnico, "{\"version\":" + version(id) + "}"))
            .andExpect(status().isOk());
        sincronizar();
        assertThat(estadoMantenimiento(mantenimiento)).isEqualTo("PROGRAMADO");
        mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), EMAIL_EXPLOTACION,
                "{\"accion\":\"APROBAR\",\"version\":" + version(id) + "}"))
            .andExpect(status().isConflict());
        assertThat(proxima(obligacion)).isEqualTo(proximaInicial);
        assertThatThrownBy(() -> jdbc.update("UPDATE facilityhub.intervenciones SET estado_validacion = 'APROBADO' WHERE id = ?", id))
            .hasMessageContaining("ck_intervencion_no_realizado");
    }

    @Test
    void p6c1_it_04_accesosCruzadosYRolesSinPermiso() throws Exception {
        Long proveedorA = insertarProveedor();
        Long proveedorB = insertarProveedor();
        String tecnicoA = emailTecnico(insertarTecnico(proveedorA));
        String tecnicoB = emailTecnico(insertarTecnico(proveedorB));
        Long mantenimiento = nuevoMantenimiento(obligacionNhoodPropia());
        insertarAsignacion(mantenimiento, proveedorA);
        sincronizar();
        mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnicoB, "{}")).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), EMAIL_INQUILINO, "{}")).andExpect(status().isForbidden());
        Long id = idDe(mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnicoA, "{}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        sincronizar();
        mvc.perform(get("/api/intervenciones/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(tecnicoB))).andExpect(status().isNotFound());
        mvc.perform(get("/api/intervenciones/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))).andExpect(status().isForbidden());
        mvc.perform(get("/api/mantenimientos/{id}/intervenciones", mantenimiento).header(HttpHeaders.AUTHORIZATION, bearer(tecnicoB)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/intervenciones/pendientes").header(HttpHeaders.AUTHORIZATION, bearer(tecnicoA))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), tecnicoA, "{\"accion\":\"APROBAR\",\"version\":0}"))
            .andExpect(status().isForbidden());
        for (String gestion : new String[] {EMAIL_ADMIN, EMAIL_EXPLOTACION}) {
            mvc.perform(get("/api/intervenciones/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(gestion)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoValidacion", is("BORRADOR")));
        }
    }

    @Test
    void p6c1_it_05_historicoP5YAtajoFinalizarCerrados() throws Exception {
        Long proveedor = insertarProveedor();
        String tecnico = emailTecnico(insertarTecnico(proveedor));
        Long historico = idMantenimientoDeLocal(LOCAL_PROPIO);
        insertarAsignacion(historico, proveedor);
        sincronizar();
        mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", historico), tecnico, "{}")).andExpect(status().isConflict());
        mvc.perform(json(patch("/api/mantenimientos/{id}/estado", historico), EMAIL_EXPLOTACION,
                "{\"estado\":\"FINALIZADO\",\"fechaRealizacion\":\"" + AYER + "\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void p6c1_it_06_auditoriaEsInmutable() {
        Long id = jdbc.queryForObject("""
            INSERT INTO facilityhub.auditoria_eventos (entidad, entidad_id, accion, usuario_id, detalle)
            VALUES ('P4-PRUEBA', 0, 'P4', (SELECT id FROM facilityhub.usuarios WHERE email = ?), '{}'::jsonb) RETURNING id""",
            Long.class, EMAIL_ADMIN);
        assertThatThrownBy(() -> jdbc.update("UPDATE facilityhub.auditoria_eventos SET accion = 'X' WHERE id = ?", id))
            .hasMessageContaining("Registro inmutable");
    }

    @Test
    void p6c1_it_07_segundaIntervencionAbiertaRechazadaEnBd() throws Exception {
        Long proveedor = insertarProveedor();
        String tecnico = emailTecnico(insertarTecnico(proveedor));
        Long mantenimiento = nuevoMantenimiento(obligacionNhoodPropia());
        insertarAsignacion(mantenimiento, proveedor);
        sincronizar();
        mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico, "{}")).andExpect(status().isCreated());
        sincronizar();
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO facilityhub.intervenciones (mantenimiento_id, proveedor_id, origen, registrada_por_usuario_id)
            SELECT mantenimiento_id, proveedor_id, origen, registrada_por_usuario_id FROM facilityhub.intervenciones
            WHERE mantenimiento_id = ?""", mantenimiento))
            .hasMessageContaining("uq_intervencion_abierta");
    }

    @Test
    void p6c1_it_08_cierreSinCumplimientoYDosIntentosLegitimosElMismoDia() throws Exception {
        Long proveedor = insertarProveedor();
        String tecnico = emailTecnico(insertarTecnico(proveedor));
        Long obligacion = obligacionNhoodPropia();
        Long mantenimiento = nuevoMantenimiento(obligacion);
        insertarAsignacion(mantenimiento, proveedor);
        LocalDate proximaInicial = proxima(obligacion);
        sincronizar();
        for (int intento = 1; intento <= 2; intento++) {
            Long id = idDe(mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico,
                    "{\"fechaEjecucion\":\"" + AYER + "\",\"actuaciones\":\"P4- visita " + intento + "\",\"resultado\":\"NO_REALIZADO\","
                        + "\"incidencias\":\"P4- local cerrado\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            Long evidencia = insertarDocumentoDeIntervencion(id, mantenimiento, "EVIDENCIA");
            sincronizar();
            mvc.perform(json(post("/api/intervenciones/{id}/envio", id), tecnico, "{\"version\":" + version(id) + "}"))
                .andExpect(status().isOk());
            sincronizar();
            mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), tecnico,
                    "{\"accion\":\"CERRAR_SIN_CUMPLIMIENTO\",\"comentario\":\"x\",\"version\":" + version(id) + "}"))
                .andExpect(status().isForbidden());
            mvc.perform(json(post("/api/intervenciones/{id}/validacion", id), EMAIL_EXPLOTACION,
                    "{\"accion\":\"CERRAR_SIN_CUMPLIMIENTO\",\"comentario\":\"P4- reprogramar\",\"version\":" + version(id) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoValidacion", is("CERRADO_SIN_CUMPLIMIENTO")))
                .andExpect(jsonPath("$.accionesPermitidas", hasSize(0)));
            sincronizar();
            assertThat(valorEnBd("estado_revision || '/' || visibilidad", evidencia)).isEqualTo("VALIDO/INTERNO");
            assertThat(estadoMantenimiento(mantenimiento)).isEqualTo("PROGRAMADO");
            assertThat(proxima(obligacion)).isEqualTo(proximaInicial);
            // Los endpoints P5 no validan ni comparten documentos de intervencion.
            mvc.perform(json(patch("/api/documentos/{id}/visibilidad", evidencia), EMAIL_EXPLOTACION, "{\"visibilidad\":\"COMPARTIDO\"}"))
                .andExpect(status().isConflict());
            mvc.perform(json(patch("/api/documentos/{id}/revision", evidencia), EMAIL_EXPLOTACION, "{\"estado\":\"RECHAZADO\"}"))
                .andExpect(status().isConflict());
        }
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM facilityhub.intervenciones WHERE mantenimiento_id = ? AND fecha_ejecucion = ?
              AND estado_validacion = 'CERRADO_SIN_CUMPLIMIENTO'""", Integer.class, mantenimiento, AYER)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM facilityhub.validaciones_intervencion v JOIN facilityhub.intervenciones i ON i.id = v.intervencion_id
            WHERE i.mantenimiento_id = ? AND v.accion = 'CERRAR_SIN_CUMPLIMIENTO'""", Integer.class, mantenimiento)).isEqualTo(2);
        // Tras los cierres se admite un nuevo intento sobre el mismo mantenimiento.
        mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico, "{}")).andExpect(status().isCreated());
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE facilityhub.intervenciones SET estado_validacion = 'CERRADO_SIN_CUMPLIMIENTO', resultado = 'CONFORME' WHERE mantenimiento_id = ? AND estado_validacion = 'CERRADO_SIN_CUMPLIMIENTO'",
            mantenimiento)).hasMessageContaining("ck_intervencion_cierre_sin_cumplimiento");
    }

    @Test
    void p6c1_it_09_intentoHistoricoAnteriorAUnCumplimientoAprobado() throws Exception {
        Long proveedor = insertarProveedor();
        String tecnico = emailTecnico(insertarTecnico(proveedor));
        Long obligacion = obligacionNhoodPropia();
        Long cumplido = nuevoMantenimiento(obligacion);
        Long otro = nuevoMantenimiento(obligacion);
        insertarAsignacion(cumplido, proveedor);
        insertarAsignacion(otro, proveedor);
        sincronizar();
        Long ok = idDe(mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", cumplido), tecnico,
                "{\"fechaEjecucion\":\"" + AYER + "\",\"actuaciones\":\"P4- ok\",\"resultado\":\"CONFORME\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        insertarDocumentoDeIntervencion(ok, cumplido, "CERTIFICADO");
        sincronizar();
        mvc.perform(json(post("/api/intervenciones/{id}/envio", ok), tecnico, "{\"version\":" + version(ok) + "}")).andExpect(status().isOk());
        sincronizar();
        mvc.perform(json(post("/api/intervenciones/{id}/validacion", ok), EMAIL_EXPLOTACION, "{\"accion\":\"APROBAR\",\"version\":" + version(ok) + "}"))
            .andExpect(status().isOk());
        sincronizar();
        LocalDate proximaAprobada = proxima(obligacion);
        String cumplidoAntes = jdbc.queryForObject("SELECT estado || '/' || fecha_realizacion FROM facilityhub.mantenimientos WHERE id = ?", String.class, cumplido);

        // Intento historico (anterior a la ejecucion aprobada) en otro mantenimiento: admitido, sin efectos sobre el cumplimiento.
        Long historico = idDe(mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", otro), tecnico,
                "{\"fechaEjecucion\":\"" + AYER.minusDays(20) + "\",\"actuaciones\":\"P4- visita antigua\",\"resultado\":\"NO_REALIZADO\","
                    + "\"incidencias\":\"P4- sin acceso\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        sincronizar();
        mvc.perform(json(post("/api/intervenciones/{id}/envio", historico), tecnico, "{\"version\":" + version(historico) + "}"))
            .andExpect(status().isOk());
        sincronizar();
        assertThat(proxima(obligacion)).isEqualTo(proximaAprobada);
        assertThat(jdbc.queryForObject("SELECT estado || '/' || fecha_realizacion FROM facilityhub.mantenimientos WHERE id = ?", String.class, cumplido))
            .isEqualTo(cumplidoAntes);
        assertThat(jdbc.queryForObject("SELECT created_at::date > fecha_ejecucion FROM facilityhub.intervenciones WHERE id = ?", Boolean.class, historico))
            .isTrue(); // fecha real del intento distinta de la fecha de registro
        // El mantenimiento ya cumplido no admite intentos.
        mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", cumplido), tecnico, "{}")).andExpect(status().isConflict());
    }

    @Test
    void p6c3_it_01_descargaDeDocumentosDeIntervencionPorRol() throws Exception {
        Long proveedorA = insertarProveedor();
        Long proveedorB = insertarProveedor();
        Long tecnicoA = insertarTecnico(proveedorA);
        String emailA = emailTecnico(tecnicoA);
        String emailB = emailTecnico(insertarTecnico(proveedorB));
        Long mantenimiento = nuevoMantenimiento(obligacionNhoodPropia());
        insertarAsignacion(mantenimiento, proveedorA);
        sincronizar();
        Long id = idDe(mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), emailA, "{}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        // Archivo sintetico en el almacenamiento temporal de la ejecucion; documento subido por el tecnico de A.
        String ruta = crearArchivoSintetico("P4-descarga-" + id + ".pdf", "%PDF-1.4 sintetico".getBytes()).toString();
        Long documento = jdbc.queryForObject("""
            INSERT INTO facilityhub.documentos_mantenimiento (mantenimiento_id, nombre_original, ruta_archivo, estado_revision,
              visibilidad, intervencion_id, categoria, subido_por_usuario_id)
            VALUES (?, 'P4-descarga.pdf', ?, 'PENDIENTE', 'INTERNO', ?, 'EVIDENCIA', ?) RETURNING id""",
            Long.class, mantenimiento, ruta, id, tecnicoA);
        sincronizar();
        String url = "/api/intervenciones/{id}/documentos/{doc}/archivo";
        mvc.perform(get(url, id, documento).header(HttpHeaders.AUTHORIZATION, bearer(emailA))).andExpect(status().isOk());
        mvc.perform(get(url, id, documento).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION))).andExpect(status().isOk());
        mvc.perform(get(url, id, documento).header(HttpHeaders.AUTHORIZATION, bearer(emailB))).andExpect(status().isNotFound());
        mvc.perform(get(url, id, 99_999_999L).header(HttpHeaders.AUTHORIZATION, bearer(emailA))).andExpect(status().isNotFound());
        mvc.perform(get(url, id, documento).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))).andExpect(status().isForbidden());
        // La ruta general P5 sigue cerrada al proveedor.
        mvc.perform(get("/api/documentos/{doc}/archivo", documento).header(HttpHeaders.AUTHORIZATION, bearer(emailA)))
            .andExpect(status().isForbidden());
    }

    // ---- Utilidades (datos sinteticos, revertidos al final de cada prueba) ----

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String email, String cuerpo) {
        return b.header(HttpHeaders.AUTHORIZATION, bearer(email)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static Long idDe(String cuerpo) {
        return Long.valueOf(cuerpo.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private long version(Long intervencion) {
        return jdbc.queryForObject("SELECT version FROM facilityhub.intervenciones WHERE id = ?", Long.class, intervencion);
    }

    private LocalDate proxima(Long obligacion) {
        return jdbc.queryForObject("SELECT proxima_fecha FROM facilityhub.obligaciones_mantenimiento WHERE id = ?", LocalDate.class, obligacion);
    }

    private String estadoMantenimiento(Long mantenimiento) {
        return jdbc.queryForObject("SELECT estado FROM facilityhub.mantenimientos WHERE id = ?", String.class, mantenimiento);
    }

    private Long obligacionNhoodPropia() {
        return jdbc.queryForObject("""
            SELECT MIN(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.responsable = 'NHOOD' AND o.fecha_inicio <= ?""", Long.class, LOCAL_PROPIO, AYER.minusDays(30));
    }

    private Long nuevoMantenimiento(Long obligacion) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.mantenimientos (obligacion_id, fecha_programada, descripcion, validacion_requerida)
            VALUES (?, ?, 'P4- mantenimiento sintetico', TRUE) RETURNING id""", Long.class, obligacion, AYER.plusDays(30));
    }

    private Long insertarDocumentoDeIntervencion(Long intervencion, Long mantenimiento, String categoria) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.documentos_mantenimiento (mantenimiento_id, nombre_original, ruta_archivo, estado_revision,
              visibilidad, intervencion_id, categoria) VALUES (?, 'P4-sintetico.pdf', 'P4-sin-archivo', 'PENDIENTE', 'INTERNO', ?, ?)
            RETURNING id""", Long.class, mantenimiento, intervencion, categoria);
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

    private String emailTecnico(Long usuarioId) {
        return jdbc.queryForObject("SELECT email FROM facilityhub.usuarios WHERE id = ?", String.class, usuarioId);
    }

    private void insertarAsignacion(Long mantenimientoId, Long proveedorId) {
        jdbc.update("""
            INSERT INTO facilityhub.asignaciones_mantenimiento (mantenimiento_id, proveedor_id, asignado_por_usuario_id)
            VALUES (?, ?, (SELECT id FROM facilityhub.usuarios WHERE email = ?))""", mantenimientoId, proveedorId, EMAIL_ADMIN);
    }
}
