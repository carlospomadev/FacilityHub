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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-006 / P6-C2 - Funcionalidades del INQUILINO con MockMvc, JWT y JPQL reales sobre facilityhub_fh005_p3 (barrera P4
 * sin cambios, rollback por prueba). Requiere V7 aplicada: si falta, aborta. Datos sinteticos con prefijo P4- revertidos.
 * La subida de archivos y las peticiones concurrentes se validan en el entorno desechable.
 */
class InquilinoP6C2P6IT extends P4IntegracionBase {
    private static final LocalDate HOY = P4RelojConfig.HOY_MADRID;

    @BeforeEach
    void exigirV7() {
        Integer v7 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.flyway_schema_history WHERE version = '7' AND success", Integer.class);
        if (v7 == null || v7 != 1) {
            throw new IllegalStateException("P6-C2 abortada: la base de pruebas no tiene V7 aplicada una vez");
        }
    }

    @Test
    void p6c2_it_01_v7ConservaLosDatosPrevios() {
        assertThat(jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) FROM facilityhub.obligacion_proveedor) + (SELECT COUNT(*) FROM facilityhub.solicitudes_proveedor)
              + (SELECT COUNT(*) FROM facilityhub.mantenimientos WHERE ocupacion_id IS NOT NULL OR fecha_prevista_inquilino IS NOT NULL)""",
            Integer.class)).isZero();
    }

    @Test
    void p6c2_it_02_flujoCompletoDelInquilino() throws Exception {
        Long obligacion = obligacionInquilinoPropia();
        Long mantenimiento = nuevoMantenimiento(obligacion, HOY.plusDays(20));
        Long proveedor = insertarProveedor("B" + (System.nanoTime() % 100000000));
        String cif = jdbc.queryForObject("SELECT cif FROM facilityhub.proveedores WHERE id = ?", String.class, proveedor);
        sincronizar();

        mvc.perform(get("/api/inquilino/obligaciones").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/inquilino/proveedores").param("cif", cif.toLowerCase()).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id", is(proveedor.intValue())));
        Long declaracion = idDe(mvc.perform(json(put("/api/inquilino/obligaciones/{id}/proveedor", obligacion), EMAIL_INQUILINO,
                "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("PROPUESTO")))
            .andReturn().getResponse().getContentAsString());
        sincronizar();
        mvc.perform(json(put("/api/inquilino/mantenimientos/{id}/asignacion", mantenimiento), EMAIL_INQUILINO, "{}"))
            .andExpect(status().isConflict()); // aun sin aceptar
        mvc.perform(json(patch("/api/declaraciones-proveedor/{id}/revision", declaracion), EMAIL_INQUILINO,
                "{\"accion\":\"ACEPTAR\",\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/declaraciones-proveedor/{id}/revision", declaracion), EMAIL_EXPLOTACION,
                "{\"accion\":\"ACEPTAR\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("ACEPTADO")));
        sincronizar();

        long version = jdbc.queryForObject("SELECT version FROM facilityhub.mantenimientos WHERE id = ?", Long.class, mantenimiento);
        mvc.perform(json(patch("/api/inquilino/mantenimientos/{id}/fecha-prevista", mantenimiento), EMAIL_INQUILINO,
                "{\"fechaPrevista\":\"" + HOY.minusDays(1) + "\",\"version\":" + version + "}")).andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/inquilino/mantenimientos/{id}/fecha-prevista", mantenimiento), EMAIL_INQUILINO,
                "{\"fechaPrevista\":\"" + HOY.plusDays(10) + "\",\"version\":" + version + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fechaPrevistaInquilino", is(HOY.plusDays(10).toString())));
        sincronizar();
        mvc.perform(json(put("/api/inquilino/mantenimientos/{id}/asignacion", mantenimiento), EMAIL_INQUILINO, "{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.proveedorId", is(proveedor.intValue())));
        sincronizar();

        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM facilityhub.mantenimientos m JOIN facilityhub.ocupaciones_local o ON o.id = m.ocupacion_id
            WHERE m.id = ? AND o.inquilino_id = ?""", Integer.class, mantenimiento, idInquilinoDelUsuario(EMAIL_INQUILINO))).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT string_agg(accion, ',' ORDER BY id) FROM facilityhub.auditoria_eventos
            WHERE (entidad = 'OBLIGACION_PROVEEDOR' AND entidad_id = ?) OR (entidad = 'MANTENIMIENTO' AND entidad_id = ?)""",
            String.class, declaracion, mantenimiento))
            .isEqualTo("DECLARAR_PROVEEDOR,ACEPTAR_PROVEEDOR,FIJAR_FECHA_PREVISTA,ASIGNAR_PROVEEDOR");
    }

    @Test
    void p6c2_it_03_restriccionesPorLocalResponsabilidadYRol() throws Exception {
        Long propia = obligacionInquilinoPropia();
        Long nhood = jdbc.queryForObject("""
            SELECT MAX(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.responsable = 'NHOOD'""", Long.class, LOCAL_PROPIO);
        Long ajena = jdbc.queryForObject("""
            SELECT MIN(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ?""", Long.class, LOCAL_AJENO);
        Long proveedor = insertarProveedor(null);
        sincronizar();
        mvc.perform(json(put("/api/inquilino/obligaciones/{id}/proveedor", nhood), EMAIL_INQUILINO, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/inquilino/obligaciones/{id}/proveedor", ajena), EMAIL_INQUILINO, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/inquilino/obligaciones/{id}/mantenimientos", ajena).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound());
        for (String gestion : new String[] {EMAIL_ADMIN, EMAIL_EXPLOTACION}) {
            mvc.perform(get("/api/inquilino/obligaciones").header(HttpHeaders.AUTHORIZATION, bearer(gestion))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/declaraciones-proveedor/pendientes").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/proveedores"), EMAIL_INQUILINO, "{\"razonSocial\":\"P4-x\"}")).andExpect(status().isForbidden());
        Long mantenimiento = nuevoMantenimiento(propia, HOY.plusDays(15));
        sincronizar();
        mvc.perform(json(put("/api/mantenimientos/{id}/asignacion", mantenimiento), EMAIL_EXPLOTACION, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/mantenimientos/{id}/asignacion", mantenimiento), EMAIL_ADMIN, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isConflict()); // sin proveedor aceptado
    }

    @Test
    void p6c2_it_04_solicitudDeProveedorNuevo() throws Exception {
        String razon = nombreSintetico("empresa");
        Long solicitud = idDe(mvc.perform(json(post("/api/inquilino/solicitudes-proveedor"), EMAIL_INQUILINO,
                "{\"razonSocial\":\"" + razon + "\",\"contacto\":\"P4- contacto\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado", is("PENDIENTE")))
            .andReturn().getResponse().getContentAsString());
        sincronizar();
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO facilityhub.solicitudes_proveedor (inquilino_id, solicitado_por_usuario_id, razon_social)
            SELECT inquilino_id, solicitado_por_usuario_id, upper(razon_social) FROM facilityhub.solicitudes_proveedor WHERE id = ?""", solicitud))
            .hasMessageContaining("uq_solicitud_pendiente");
    }

    @Test
    void p6c2_it_05_resolucionDeSolicitudSoloAdmin() throws Exception {
        Long solicitud = jdbc.queryForObject("""
            INSERT INTO facilityhub.solicitudes_proveedor (inquilino_id, solicitado_por_usuario_id, razon_social)
            VALUES (?, (SELECT id FROM facilityhub.usuarios WHERE email = ?), ?) RETURNING id""",
            Long.class, idInquilinoDelUsuario(EMAIL_INQUILINO), EMAIL_INQUILINO, nombreSintetico("solicitada"));
        Long proveedor = insertarProveedor(null);
        sincronizar();
        String cuerpo = "{\"accion\":\"ATENDER\",\"proveedorId\":" + proveedor + ",\"version\":0}";
        mvc.perform(json(patch("/api/solicitudes-proveedor/{id}", solicitud), EMAIL_EXPLOTACION, cuerpo)).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/solicitudes-proveedor/{id}", solicitud), EMAIL_INQUILINO, cuerpo)).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/solicitudes-proveedor/{id}", solicitud), EMAIL_ADMIN, cuerpo))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("ATENDIDA")));
        sincronizar();
        mvc.perform(get("/api/inquilino/solicitudes-proveedor").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].proveedorId", is(proveedor.intValue())));
    }

    @Test
    void p6c2_it_06_soloCertificadosAprobadosYCompartidos() throws Exception {
        Long obligacion = obligacionInquilinoPropia();
        Long aprobado = nuevoMantenimiento(obligacion, HOY.minusDays(5));
        Long proveedor = insertarProveedor(null);
        Long intervencion = jdbc.queryForObject("""
            INSERT INTO facilityhub.intervenciones (mantenimiento_id, proveedor_id, origen, registrada_por_usuario_id, numero_envio,
              fecha_ejecucion, resultado, estado_validacion, enviada_at, enviada_por_usuario_id)
            VALUES (?, ?, 'REGISTRO_GESTION', (SELECT id FROM facilityhub.usuarios WHERE email = ?), 1, ?, 'CONFORME', 'APROBADO',
              now(), (SELECT id FROM facilityhub.usuarios WHERE email = ?)) RETURNING id""",
            Long.class, aprobado, proveedor, EMAIL_EXPLOTACION, HOY.minusDays(6), EMAIL_EXPLOTACION);
        Long certificado = documentoDeIntervencion(intervencion, aprobado, "CERTIFICADO", "COMPARTIDO");
        Long interno = documentoDeIntervencion(intervencion, aprobado, "EVIDENCIA", "INTERNO");
        sincronizar();
        String cuerpo = mvc.perform(get("/api/inquilino/obligaciones/{id}/mantenimientos", obligacion)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).contains("\"certificados\":[{\"id\":" + certificado + ",").doesNotContain("\"id\":" + interno + ",");
    }

    @Test
    void p6c2_it_07_cambioDeOcupacionProtegeElHistorial() throws Exception {
        Long obligacion = obligacionInquilinoPropia();
        Long anterior = nuevoMantenimiento(obligacion, HOY.plusDays(20));
        Long proveedor = insertarProveedor(null);
        Long ocupacionVieja = jdbc.queryForObject("""
            SELECT o.id FROM facilityhub.ocupaciones_local o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.activo""", Long.class, LOCAL_PROPIO);
        jdbc.update("""
            INSERT INTO facilityhub.obligacion_proveedor (obligacion_id, proveedor_id, ocupacion_id, estado, declarado_por_usuario_id,
              declarado_at, revisado_por_usuario_id, revisado_at)
            VALUES (?, ?, ?, 'ACEPTADO', (SELECT id FROM facilityhub.usuarios WHERE email = ?), now(),
              (SELECT id FROM facilityhub.usuarios WHERE email = ?), now())""",
            obligacion, proveedor, ocupacionVieja, EMAIL_INQUILINO, EMAIL_EXPLOTACION);
        jdbc.update("UPDATE facilityhub.mantenimientos SET ocupacion_id = ? WHERE id = ?", ocupacionVieja, anterior);
        // Cambio de ocupante: la ocupacion anterior termina ayer y entra el inquilino del otro local.
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET activo = FALSE, fecha_fin = ? WHERE id = ?", HOY.minusDays(1), ocupacionVieja);
        jdbc.update("""
            INSERT INTO facilityhub.ocupaciones_local (local_id, inquilino_id, fecha_inicio, activo)
            VALUES ((SELECT id FROM facilityhub.locales WHERE codigo = ?), ?, ?, TRUE)""",
            LOCAL_PROPIO, idInquilinoOcupante(LOCAL_AJENO), HOY);
        Long nuevo = nuevoMantenimiento(obligacion, HOY.plusDays(25));
        sincronizar();

        mvc.perform(get("/api/inquilino/obligaciones/{id}/mantenimientos", obligacion).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isNotFound());
        mvc.perform(json(put("/api/mantenimientos/{id}/asignacion", nuevo), EMAIL_ADMIN, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isConflict()); // la aceptacion era de la ocupacion anterior
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.obligacion_proveedor WHERE obligacion_id = ?", Integer.class, obligacion))
            .isEqualTo(1); // historial conservado
        assertThat(anterior).isNotEqualTo(nuevo);
    }

    @Test
    void p6c2_it_08_barrerasEnBd() {
        Long nhood = jdbc.queryForObject("""
            SELECT MAX(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.responsable = 'NHOOD'""", Long.class, LOCAL_PROPIO);
        Long ocupacion = jdbc.queryForObject("""
            SELECT o.id FROM facilityhub.ocupaciones_local o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.activo""", Long.class, LOCAL_PROPIO);
        Long mantenimientoN = nuevoMantenimiento(nhood, HOY.plusDays(5));
        assertThatThrownBy(() -> jdbc.update("UPDATE facilityhub.mantenimientos SET ocupacion_id = ? WHERE id = ?", ocupacion, mantenimientoN))
            .hasMessageContaining("La ocupacion del mantenimiento no corresponde");
    }

    @Test
    void p6c2_it_09_declaracionSobreObligacionNhoodRechazadaEnBd() {
        Long nhood = jdbc.queryForObject("""
            SELECT MAX(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.responsable = 'NHOOD'""", Long.class, LOCAL_PROPIO);
        Long ocupacion = jdbc.queryForObject("""
            SELECT o.id FROM facilityhub.ocupaciones_local o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.activo""", Long.class, LOCAL_PROPIO);
        Long proveedor = insertarProveedor(null);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO facilityhub.obligacion_proveedor (obligacion_id, proveedor_id, ocupacion_id, declarado_por_usuario_id, declarado_at)
            VALUES (?, ?, ?, (SELECT id FROM facilityhub.usuarios WHERE email = ?), now())""", nhood, proveedor, ocupacion, EMAIL_INQUILINO))
            .hasMessageContaining("La declaracion de proveedor exige");
    }

    // ---- Utilidades (datos sinteticos, revertidos al final de cada prueba) ----

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String email, String cuerpo) {
        return b.header(HttpHeaders.AUTHORIZATION, bearer(email)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static Long idDe(String cuerpo) {
        return Long.valueOf(cuerpo.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    // Primera obligacion del local propio pasada a responsabilidad INQUILINO (dentro de la transaccion revertida).
    private Long obligacionInquilinoPropia() {
        Long id = jdbc.queryForObject("""
            SELECT MIN(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ?""", Long.class, LOCAL_PROPIO);
        jdbc.update("UPDATE facilityhub.obligaciones_mantenimiento SET responsable = 'INQUILINO' WHERE id = ?", id);
        return id;
    }

    private Long nuevoMantenimiento(Long obligacion, LocalDate fecha) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.mantenimientos (obligacion_id, fecha_programada, descripcion, validacion_requerida)
            VALUES (?, ?, 'P4- mantenimiento sintetico', TRUE) RETURNING id""", Long.class, obligacion, fecha);
    }

    private Long insertarProveedor(String cif) {
        return jdbc.queryForObject("INSERT INTO facilityhub.proveedores (razon_social, cif, activo) VALUES (?, ?, TRUE) RETURNING id",
            Long.class, nombreSintetico("proveedor"), cif);
    }

    private Long documentoDeIntervencion(Long intervencion, Long mantenimiento, String categoria, String visibilidad) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.documentos_mantenimiento (mantenimiento_id, nombre_original, ruta_archivo, estado_revision,
              visibilidad, intervencion_id, categoria) VALUES (?, 'P4-sintetico.pdf', 'P4-sin-archivo', 'VALIDO', ?, ?, ?)
            RETURNING id""", Long.class, mantenimiento, visibilidad, intervencion, categoria);
    }
}
