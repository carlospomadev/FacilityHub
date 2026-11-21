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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-006 / P6-D3-1 - Panel de gestion con MockMvc, JWT y JPQL reales sobre facilityhub_fh005_p3 (barrera P4 sin cambios,
 * rollback por prueba). Requiere V9. Datos sinteticos con prefijo P4- revertidos; las comprobaciones de clasificacion se
 * limitan a los mantenimientos sinteticos (la base tiene otros datos), las de coherencia comparan totales.
 */
class PanelGestionP6D3P6IT extends P4IntegracionBase {
    private static final LocalDate HOY = P4RelojConfig.HOY_MADRID;
    private static final String RESUMEN = "/api/gestion/resumen";
    private static final String TRABAJOS = "/api/gestion/trabajos";
    private static final String[] FILTROS = {"VENCIDOS", "PROXIMOS", "PENDIENTE_VALIDACION", "REQUIERE_CORRECCION", "SIN_ASIGNAR"};

    @BeforeEach
    void exigirV9() {
        Integer v9 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.flyway_schema_history WHERE version = '9' AND success", Integer.class);
        if (v9 == null || v9 != 1) {
            throw new IllegalStateException("P6-D3 abortada: la base de pruebas no tiene V9 aplicada una vez");
        }
    }

    @Test
    void p6d3_it_01_clasificacionDeLosFiltros() throws Exception {
        Long obligacion = obligacionNhood();
        Long proveedor = insertarProveedor();
        String tecnico = email(insertarTecnico(proveedor));
        Long vencido = nuevoMantenimiento(obligacion, HOY.minusDays(3));
        Long limite = nuevoMantenimiento(obligacion, HOY.plusDays(15));
        Long fuera = nuevoMantenimiento(obligacion, HOY.plusDays(16));
        Long cancelado = nuevoMantenimiento(obligacion, HOY.minusDays(2));
        jdbc.update("UPDATE facilityhub.mantenimientos SET estado = 'CANCELADO' WHERE id = ?", cancelado);
        Long comun = nuevoMantenimiento(obligacionZonaComun(), HOY.minusDays(1));
        Long enviado = nuevoMantenimiento(obligacion, HOY.minusDays(4));
        Long corregir = nuevoMantenimiento(obligacion, HOY.minusDays(2));
        Long aprobado = nuevoMantenimiento(obligacion, HOY.minusDays(5));
        sincronizar();

        Long iEnviada = enviar(enviado, proveedor, tecnico);
        Long iCorregir = enviar(corregir, proveedor, tecnico);
        Long iAprobada = enviar(aprobado, proveedor, tecnico);
        validar(iCorregir, "SOLICITAR_CORRECCION");
        validar(iAprobada, "APROBAR");
        assertThat(estado(enviado)).isEqualTo("FINALIZADO");

        List<Long> sinteticos = List.of(vencido, limite, fuera, cancelado, comun, enviado, corregir, aprobado);
        // FINALIZADO con intervencion enviada: pendiente de validacion, nunca vencido. APROBADO y CANCELADO fuera.
        assertThat(ids("VENCIDOS", sinteticos)).containsExactly(vencido, corregir, comun);
        assertThat(ids("PROXIMOS", sinteticos)).containsExactly(limite);
        assertThat(ids("PENDIENTE_VALIDACION", sinteticos)).containsExactly(enviado);
        assertThat(ids("REQUIERE_CORRECCION", sinteticos)).containsExactly(corregir);
        // corregir conserva la asignacion activa del proveedor; los demas abiertos no tienen asignacion.
        assertThat(ids("SIN_ASIGNAR", sinteticos)).containsExactly(vencido, comun, limite);

        Map<String, Object> filaComun = fila("VENCIDOS", comun);
        assertThat(filaComun.get("zonaComun")).isEqualTo(true);
        assertThat(filaComun.get("local")).isNull();
        assertThat((String) filaComun.get("centro")).isNotBlank();
        assertThat(numero(filaComun, "diasHastaVencimiento")).isEqualTo(-1);
        Map<String, Object> filaEnviada = fila("PENDIENTE_VALIDACION", enviado);
        assertThat(numero(filaEnviada, "intervencionId")).isEqualTo(iEnviada);
        assertThat(filaEnviada.get("estado")).isEqualTo("FINALIZADO");
        assertThat((String) filaEnviada.get("proveedorAsignado")).startsWith("P4-");
        assertThat(fila("VENCIDOS", corregir).get("estadoIntervencion")).isEqualTo("REQUIERE_CORRECCION");
        assertThat(numero(fila("PROXIMOS", limite), "diasHastaVencimiento")).isEqualTo(15);
    }

    @Test
    void p6d3_it_02_resumenCoherenteConLosListados() throws Exception {
        Long obligacion = obligacionNhood();
        nuevoMantenimiento(obligacion, HOY.minusDays(1));
        nuevoMantenimiento(obligacion, HOY.plusDays(1));
        nuevoMantenimiento(obligacionZonaComun(), HOY.plusDays(2));
        sincronizar();
        for (String email : new String[] {EMAIL_EXPLOTACION, EMAIL_ADMIN}) {
            Map<String, Object> r = JsonPath.read(leer(get(RESUMEN), email), "$");
            assertThat(r.get("fecha")).isEqualTo(HOY.toString());
            assertThat((int) numero(r, "preavisoDias")).isEqualTo(15);
            assertThat((int) numero(r, "vencidos")).isEqualTo(lista("VENCIDOS", email).size());
            assertThat((int) numero(r, "proximos")).isEqualTo(lista("PROXIMOS", email).size());
            assertThat((int) numero(r, "pendientesValidacion")).isEqualTo(lista("PENDIENTE_VALIDACION", email).size());
            assertThat((int) numero(r, "requierenCorreccion")).isEqualTo(lista("REQUIERE_CORRECCION", email).size());
            assertThat((int) numero(r, "sinAsignar")).isEqualTo(lista("SIN_ASIGNAR", email).size());
            // Mismas bandejas que los endpoints existentes de gestion.
            assertThat((int) numero(r, "pendientesValidacion")).isEqualTo(filas(leer(get("/api/intervenciones/pendientes"), email)).size());
            assertThat((int) numero(r, "declaracionesPendientes"))
                .isEqualTo(filas(leer(get("/api/declaraciones-proveedor/pendientes"), email)).size());
            assertThat((int) numero(r, "solicitudesPendientes")).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM facilityhub.solicitudes_proveedor WHERE estado = 'PENDIENTE'", Integer.class));
            assertThat((int) numero(r, "vencidos")).isPositive();
            assertThat((int) numero(r, "proximos")).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void p6d3_it_03_accesoPorRol() throws Exception {
        String tecnico = email(insertarTecnico(insertarProveedor()));
        sincronizar();
        for (String email : new String[] {EMAIL_INQUILINO, tecnico}) {
            mvc.perform(get(RESUMEN).header(HttpHeaders.AUTHORIZATION, bearer(email))).andExpect(status().isForbidden());
            mvc.perform(get(TRABAJOS).param("filtro", "VENCIDOS").header(HttpHeaders.AUTHORIZATION, bearer(email)))
                .andExpect(status().isForbidden());
        }
        mvc.perform(get(RESUMEN)).andExpect(status().isUnauthorized());
        mvc.perform(post(RESUMEN).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION))).andExpect(status().isForbidden());
        mvc.perform(get(TRABAJOS).param("filtro", "OTRO").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isBadRequest());
        mvc.perform(get(TRABAJOS).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))).andExpect(status().isBadRequest());
    }

    @Test
    void p6d3_it_04_consultasSinEscrituras() throws Exception {
        nuevoMantenimiento(obligacionNhood(), HOY.minusDays(2));
        sincronizar();
        String antes = huella();
        leer(get(RESUMEN), EMAIL_EXPLOTACION);
        for (String f : FILTROS) {
            lista(f, EMAIL_EXPLOTACION);
            lista(f, EMAIL_ADMIN);
        }
        sincronizar();
        assertThat(huella()).isEqualTo(antes);
    }

    // ---- Utilidades (datos sinteticos, revertidos al final de cada prueba) ----

    // Huella de las tablas que podria tocar un efecto lateral: filas, versiones y marcas de actualizacion.
    private String huella() {
        return jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) || ':' || COALESCE(SUM(version), 0) || ':' || COALESCE(MAX(updated_at)::text, '')
                      FROM facilityhub.mantenimientos)
                || '|' || (SELECT COUNT(*) || ':' || COALESCE(SUM(version), 0) FROM facilityhub.intervenciones)
                || '|' || (SELECT COUNT(*) FROM facilityhub.asignaciones_mantenimiento)
                || '|' || (SELECT COUNT(*) FROM facilityhub.alertas)
                || '|' || (SELECT COUNT(*) FROM facilityhub.auditoria_eventos)
                || '|' || (SELECT COUNT(*) || ':' || COALESCE(MAX(proxima_fecha)::text, '') FROM facilityhub.obligaciones_mantenimiento)
            """, String.class);
    }

    private List<Long> ids(String filtro, List<Long> sinteticos) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> n : lista(filtro, EMAIL_EXPLOTACION)) {
            long id = numero(n, "mantenimientoId");
            if (sinteticos.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<String, Object> fila(String filtro, Long mantenimiento) throws Exception {
        for (Map<String, Object> n : lista(filtro, EMAIL_EXPLOTACION)) {
            if (numero(n, "mantenimientoId") == mantenimiento) {
                return n;
            }
        }
        throw new AssertionError("Mantenimiento " + mantenimiento + " ausente en " + filtro);
    }

    private List<Map<String, Object>> lista(String filtro, String email) throws Exception {
        return filas(leer(get(TRABAJOS).param("filtro", filtro), email));
    }

    private static List<Map<String, Object>> filas(String json) {
        return JsonPath.read(json, "$");
    }

    private static long numero(Map<String, Object> fila, String campo) {
        return ((Number) fila.get(campo)).longValue();
    }

    private String leer(MockHttpServletRequestBuilder b, String email) throws Exception {
        return mvc.perform(b.header(HttpHeaders.AUTHORIZATION, bearer(email)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // Asigna, registra (CONFORME), adjunta una evidencia sintetica y envia: queda en PENDIENTE_VALIDACION.
    private Long enviar(Long mantenimiento, Long proveedor, String tecnico) throws Exception {
        mvc.perform(json(put("/api/mantenimientos/{id}/asignacion", mantenimiento), EMAIL_EXPLOTACION, "{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isOk());
        String fecha = jdbc.queryForObject("SELECT fecha_programada::text FROM facilityhub.mantenimientos WHERE id = ?", String.class, mantenimiento);
        String alta = mvc.perform(json(post("/api/mantenimientos/{id}/intervenciones", mantenimiento), tecnico,
                "{\"fechaEjecucion\":\"" + fecha + "\",\"actuaciones\":\"P4- revision\",\"resultado\":\"CONFORME\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long intervencion = ((Number) JsonPath.read(alta, "$.id")).longValue();
        jdbc.update("""
            INSERT INTO facilityhub.documentos_mantenimiento (mantenimiento_id, nombre_original, ruta_archivo, estado_revision,
              visibilidad, intervencion_id, categoria) VALUES (?, 'P4-sintetico.pdf', 'P4-sin-archivo', 'PENDIENTE', 'INTERNO', ?, 'EVIDENCIA')""",
            mantenimiento, intervencion);
        sincronizar();
        mvc.perform(json(post("/api/intervenciones/{id}/envio", intervencion), tecnico, "{\"version\":" + version(intervencion) + "}"))
            .andExpect(status().isOk());
        sincronizar();
        return intervencion;
    }

    private void validar(Long intervencion, String accion) throws Exception {
        mvc.perform(json(post("/api/intervenciones/{id}/validacion", intervencion), EMAIL_EXPLOTACION,
                "{\"accion\":\"" + accion + "\",\"comentario\":\"P4- revision\",\"version\":" + version(intervencion) + "}"))
            .andExpect(status().isOk());
        sincronizar();
    }

    private Long version(Long intervencion) {
        return jdbc.queryForObject("SELECT version FROM facilityhub.intervenciones WHERE id = ?", Long.class, intervencion);
    }

    private String estado(Long mantenimiento) {
        return jdbc.queryForObject("SELECT estado FROM facilityhub.mantenimientos WHERE id = ?", String.class, mantenimiento);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String email, String cuerpo) {
        return b.header(HttpHeaders.AUTHORIZATION, bearer(email)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private String email(Long usuarioId) {
        return jdbc.queryForObject("SELECT email FROM facilityhub.usuarios WHERE id = ?", String.class, usuarioId);
    }

    private Long obligacionNhood() {
        return jdbc.queryForObject("""
            SELECT MIN(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ? AND o.responsable = 'NHOOD' AND o.fecha_inicio <= ?""", Long.class, LOCAL_PROPIO, HOY.minusDays(100));
    }

    // Obligacion sintetica de zona comun (sin local) en el centro del local propio.
    private Long obligacionZonaComun() {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.obligaciones_mantenimiento (centro_id, local_id, tipo_mantenimiento_id, fecha_inicio,
              proxima_fecha, responsable, observaciones)
            SELECT l.centro_id, NULL, (SELECT MIN(id) FROM facilityhub.tipos_mantenimiento), ?, ?, 'NHOOD', 'P4- zona comun'
            FROM facilityhub.locales l WHERE l.codigo = ? RETURNING id""",
            Long.class, HOY.minusDays(200), HOY.plusDays(30), LOCAL_PROPIO);
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
