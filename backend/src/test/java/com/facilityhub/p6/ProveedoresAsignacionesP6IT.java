package com.facilityhub.p6;

import com.facilityhub.p4.P4IntegracionBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-006 / P6-B - Proveedores, asignaciones, trabajos del proveedor y coherencia del esquema V5, con MockMvc,
 * JWT y JPQL reales sobre facilityhub_fh005_p3 (barrera P4 sin cambios, rollback por prueba).
 * Requiere V5 aplicada en la base de pruebas: si falta, aborta antes de ejecutar nada.
 * Datos sinteticos con prefijo P4- dentro de la transaccion revertida. El sufijo *P6IT no lo ejecuta Surefire por defecto.
 */
class ProveedoresAsignacionesP6IT extends P4IntegracionBase {
    private static final String CONTRASENA_SINTETICA = "contrasena-sintetica-p6b";

    @BeforeEach
    void exigirV5() {
        Integer v5 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.flyway_schema_history WHERE version = '5' AND success", Integer.class);
        if (v5 == null || v5 != 1) {
            throw new IllegalStateException("P6 abortada: la base de pruebas no tiene V5 aplicada una vez");
        }
    }

    // ---- Esquema V5 sobre los datos existentes ----

    @Test
    void p6b_it_01_datosPreviosQuedanCoherentesTrasV5() {
        Integer sinCentro = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.obligaciones_mantenimiento WHERE centro_id IS NULL", Integer.class);
        Integer centroIncoherente = jdbc.queryForObject("""
            SELECT COUNT(*) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE o.centro_id <> l.centro_id""", Integer.class);
        Integer historicoP5 = jdbc.queryForObject("""
            SELECT COUNT(*) FROM facilityhub.mantenimientos m
            WHERE m.validacion_requerida = FALSE""", Integer.class);
        Integer obligacionesDemoNhood = jdbc.queryForObject("""
            SELECT COUNT(*) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo IN ('L-101', 'L-205') AND o.responsable = 'NHOOD'""", Integer.class);
        assertThat(sinCentro).isZero();
        assertThat(centroIncoherente).isZero();
        assertThat(historicoP5).isPositive();
        assertThat(obligacionesDemoNhood).isGreaterThanOrEqualTo(3);
    }

    // ---- Coherencia en BD (una violacion por prueba: la transaccion queda abortada y se revierte) ----

    @Test
    void p6b_it_02_obligacionDeInquilinoSinLocalEsRechazada() {
        Long centro = jdbc.queryForObject("SELECT centro_id FROM facilityhub.locales WHERE codigo = 'L-101'", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO facilityhub.obligaciones_mantenimiento (centro_id, local_id, tipo_mantenimiento_id, fecha_inicio,
              proxima_fecha, responsable) VALUES (?, NULL, (SELECT MIN(id) FROM facilityhub.tipos_mantenimiento),
              DATE '2026-01-01', DATE '2026-12-31', 'INQUILINO')""", centro))
            .hasMessageContaining("ck_obligacion_responsable_local");
    }

    @Test
    void p6b_it_03_instalacionDeOtroLocalEsRechazada() {
        Long instalacionL101 = jdbc.queryForObject("""
            SELECT MIN(i.id) FROM facilityhub.instalaciones i JOIN facilityhub.locales l ON l.id = i.local_id
            WHERE l.codigo = 'L-101'""", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO facilityhub.obligaciones_mantenimiento (local_id, instalacion_id, tipo_mantenimiento_id, fecha_inicio,
              proxima_fecha, responsable) VALUES (?, ?, (SELECT MAX(id) FROM facilityhub.tipos_mantenimiento),
              DATE '2026-01-01', DATE '2026-12-31', 'NHOOD')""", idLocal(LOCAL_AJENO), instalacionL101))
            .hasMessageContaining("La instalacion no pertenece al local");
    }

    @Test
    void p6b_it_04_localDeOtroCentroEsRechazado() {
        Long otroCentro = jdbc.queryForObject(
            "INSERT INTO facilityhub.centros (codigo, nombre) VALUES (?, ?) RETURNING id", Long.class,
            nombreSintetico("C"), nombreSintetico("centro"));
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO facilityhub.obligaciones_mantenimiento (centro_id, local_id, tipo_mantenimiento_id, fecha_inicio,
              proxima_fecha, responsable) VALUES (?, ?, (SELECT MIN(id) FROM facilityhub.tipos_mantenimiento),
              DATE '2026-01-01', DATE '2026-12-31', 'NHOOD')""", otroCentro, idLocal(LOCAL_PROPIO)))
            .hasMessageContaining("fk_obligacion_local_centro");
    }

    @Test
    void p6b_it_05_usuarioProveedorSinEmpresaEsRechazado() {
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO facilityhub.usuarios (nombre, email, password_hash, rol_id)
            VALUES ('P4-sin-empresa', ?, 'x', (SELECT id FROM facilityhub.roles WHERE codigo = 'PROVEEDOR'))""",
            nombreSintetico("sin-empresa") + "@sintetico.test"))
            .hasMessageContaining("Un usuario PROVEEDOR debe tener proveedor_id");
    }

    @Test
    void p6b_it_06_dosAsignacionesActivasEsRechazado() {
        Long proveedor = insertarProveedor(true);
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        insertarAsignacion(mantenimiento, proveedor, null);
        assertThatThrownBy(() -> insertarAsignacion(mantenimiento, proveedor, null))
            .hasMessageContaining("uq_asignacion_activa");
    }

    @Test
    void p6b_it_07_tecnicoDeOtraEmpresaEnAsignacionEsRechazado() {
        Long proveedorA = insertarProveedor(true);
        Long proveedorB = insertarProveedor(true);
        Long tecnicoB = insertarTecnico(proveedorB);
        assertThatThrownBy(() -> insertarAsignacion(idMantenimientoDeLocal(LOCAL_PROPIO), proveedorA, tecnicoB))
            .hasMessageContaining("fk_asignacion_tecnico_proveedor");
    }

    // ---- Acceso del PROVEEDOR ----

    @Test
    void p6b_it_08_proveedorVeSoloSusTrabajosY404UniformeParaElResto() throws Exception {
        Long proveedorA = insertarProveedor(true);
        Long proveedorB = insertarProveedor(true);
        String emailA = emailTecnico(insertarTecnico(proveedorA));
        Long propio = idMantenimientoDeLocal(LOCAL_PROPIO);
        Long ajeno = idMantenimientoDeLocal(LOCAL_AJENO);
        insertarAsignacion(propio, proveedorA, null);
        insertarAsignacion(ajeno, proveedorB, null);
        sincronizar();

        mvc.perform(get("/api/proveedor/trabajos").header(HttpHeaders.AUTHORIZATION, bearer(emailA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].mantenimientoId", is(propio.intValue())))
            .andExpect(jsonPath("$[0].observaciones").doesNotExist())
            .andExpect(jsonPath("$[0].accionesPermitidas", hasSize(0)));
        String cuerpoAjeno = mvc.perform(get("/api/proveedor/trabajos/{id}", ajeno).header(HttpHeaders.AUTHORIZATION, bearer(emailA)))
            .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String cuerpoInexistente = mvc.perform(get("/api/proveedor/trabajos/{id}", 99_999_999L).header(HttpHeaders.AUTHORIZATION, bearer(emailA)))
            .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(cuerpoAjeno.replaceAll("\"instance\":\"[^\"]*\"", ""))
            .isEqualTo(cuerpoInexistente.replaceAll("\"instance\":\"[^\"]*\"", ""));
    }

    @Test
    void p6b_it_09_proveedorNoAccedeARutasGenerales() throws Exception {
        String email = emailTecnico(insertarTecnico(insertarProveedor(true)));
        sincronizar();
        String token = bearer(email);
        Long local = idLocal(LOCAL_PROPIO);
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        for (String ruta : new String[] {"/api/centros", "/api/locales/" + local + "/obligaciones",
            "/api/locales/" + local + "/instalaciones", "/api/mantenimientos/" + mantenimiento + "/documentos",
            "/api/proveedores", "/api/mantenimientos/" + mantenimiento + "/asignacion"}) {
            mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        }
    }

    @Test
    void p6b_it_10_reasignarQuitaElAccesoAlProveedorAnterior() throws Exception {
        Long proveedorA = insertarProveedor(true);
        Long proveedorB = insertarProveedor(true);
        String emailA = emailTecnico(insertarTecnico(proveedorA));
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        insertarAsignacion(mantenimiento, proveedorA, null);
        sincronizar();

        mvc.perform(put("/api/mantenimientos/{id}/asignacion", mantenimiento)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION))
                .contentType(MediaType.APPLICATION_JSON).content("{\"proveedorId\":" + proveedorB + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proveedorId", is(proveedorB.intValue())));
        sincronizar();

        mvc.perform(get("/api/proveedor/trabajos").header(HttpHeaders.AUTHORIZATION, bearer(emailA)))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        Integer activas = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.asignaciones_mantenimiento WHERE mantenimiento_id = ? AND activa", Integer.class, mantenimiento);
        String motivoAnterior = jdbc.queryForObject("""
            SELECT motivo_fin FROM facilityhub.asignaciones_mantenimiento WHERE mantenimiento_id = ? AND proveedor_id = ?""",
            String.class, mantenimiento, proveedorA);
        assertThat(activas).isEqualTo(1);
        assertThat(motivoAnterior).isEqualTo("REASIGNADA");
    }

    // ---- Permisos de gestion ----

    @Test
    void p6b_it_11_soloAdminDaDeAltaProveedoresYTecnicos() throws Exception {
        String cuerpo = "{\"razonSocial\":\"" + nombreSintetico("prov") + "\"}";
        mvc.perform(post("/api/proveedores").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION))
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(post("/api/proveedores").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        String creado = mvc.perform(post("/api/proveedores").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(creado.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String email = nombreSintetico("tec").toLowerCase() + "@sintetico.test";
        mvc.perform(post("/api/proveedores/{id}/usuarios", id).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"P4-tecnico\",\"email\":\"" + email + "\",\"password\":\"" + CONTRASENA_SINTETICA + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        sincronizar();
        String rol = jdbc.queryForObject("""
            SELECT r.codigo FROM facilityhub.usuarios u JOIN facilityhub.roles r ON r.id = u.rol_id WHERE u.email = ?""",
            String.class, email);
        String hash = jdbc.queryForObject("SELECT password_hash FROM facilityhub.usuarios WHERE email = ?", String.class, email);
        assertThat(rol).isEqualTo("PROVEEDOR");
        assertThat(hash).startsWith("$2").doesNotContain(CONTRASENA_SINTETICA);

        mvc.perform(get("/api/proveedores").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION))).andExpect(status().isOk());
        mvc.perform(get("/api/proveedores").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))).andExpect(status().isForbidden());
    }

    @Test
    void p6b_it_12_inquilinoYProveedorNoAsignan() throws Exception {
        Long proveedor = insertarProveedor(true);
        String emailTec = emailTecnico(insertarTecnico(proveedor));
        sincronizar();
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        String cuerpo = "{\"proveedorId\":" + proveedor + "}";
        mvc.perform(put("/api/mantenimientos/{id}/asignacion", mantenimiento).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(put("/api/mantenimientos/{id}/asignacion", mantenimiento).header(HttpHeaders.AUTHORIZATION, bearer(emailTec))
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        Integer asignaciones = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.asignaciones_mantenimiento WHERE mantenimiento_id = ?", Integer.class, mantenimiento);
        assertThat(asignaciones).isZero();
    }

    @Test
    void p6b_it_13_obligacionDelInquilinoNoLaAsignaExplotacion() throws Exception {
        Long obligacion = jdbc.queryForObject("""
            SELECT MIN(o.id) FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ?""", Long.class, LOCAL_PROPIO);
        mvc.perform(patch("/api/obligaciones/{id}/responsable", obligacion).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION))
                .contentType(MediaType.APPLICATION_JSON).content("{\"responsable\":\"INQUILINO\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.responsable", is("INQUILINO")));
        sincronizar();
        Long mantenimiento = jdbc.queryForObject(
            "SELECT MIN(id) FROM facilityhub.mantenimientos WHERE obligacion_id = ?", Long.class, obligacion);
        Long proveedor = insertarProveedor(true);
        sincronizar();
        mvc.perform(put("/api/mantenimientos/{id}/asignacion", mantenimiento).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION))
                .contentType(MediaType.APPLICATION_JSON).content("{\"proveedorId\":" + proveedor + "}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/obligaciones/{id}/responsable", obligacion).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"responsable\":\"NHOOD\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void p6b_it_14_bajaDelProveedorCierraAsignacionesYVaciaSusTrabajos() throws Exception {
        Long proveedor = insertarProveedor(true);
        String email = emailTecnico(insertarTecnico(proveedor));
        Long mantenimiento = idMantenimientoDeLocal(LOCAL_PROPIO);
        insertarAsignacion(mantenimiento, proveedor, null);
        sincronizar();
        mvc.perform(patch("/api/proveedores/{id}", proveedor).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());
        sincronizar();
        String motivo = jdbc.queryForObject(
            "SELECT motivo_fin FROM facilityhub.asignaciones_mantenimiento WHERE proveedor_id = ?", String.class, proveedor);
        assertThat(motivo).isEqualTo("PROVEEDOR_BAJA");
        mvc.perform(get("/api/proveedor/trabajos").header(HttpHeaders.AUTHORIZATION, bearer(email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- Utilidades (datos sinteticos, revertidos al final de cada prueba) ----

    private Long insertarProveedor(boolean activo) {
        return jdbc.queryForObject("INSERT INTO facilityhub.proveedores (razon_social, activo) VALUES (?, ?) RETURNING id",
            Long.class, nombreSintetico("proveedor"), activo);
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

    private Long insertarAsignacion(Long mantenimientoId, Long proveedorId, Long tecnicoId) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.asignaciones_mantenimiento (mantenimiento_id, proveedor_id, tecnico_usuario_id,
              asignado_por_usuario_id) VALUES (?, ?, ?, (SELECT id FROM facilityhub.usuarios WHERE email = ?)) RETURNING id""",
            Long.class, mantenimientoId, proveedorId, tecnicoId, EMAIL_ADMIN);
    }
}
