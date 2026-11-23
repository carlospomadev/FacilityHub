package com.facilityhub.fh010;

import com.facilityhub.p4.P4IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-010A / RF-07 - Alta y consulta de instalaciones con MockMvc, filtro JWT y PostgreSQL reales.
 * Misma infraestructura P4 (barrera de entorno: solo la base desechable autorizada; rollback por prueba).
 * Datos sinteticos con prefijo P4-. El sufijo *P6IT no coincide con los patrones por defecto de Surefire.
 */
class InstalacionesFH010AP6IT extends P4IntegracionBase {

    @Test
    void fh010a_it_01_adminRegistraEnUnLocalYQuedaPersistidaYVisible() throws Exception {
        Long local = idLocal(LOCAL_PROPIO);
        Long centro = centroDe(local);
        String nombre = nombreSintetico("ins");
        Long id = idCreado(alta(EMAIL_ADMIN, centro, Map.of("localId", local, "nombre", nombre, "tipo", "PCI"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.centroId").value(centro))
            .andExpect(jsonPath("$.localId").value(local))
            .andExpect(jsonPath("$.estado").value("ACTIVA")));
        sincronizar();

        Map<String, Object> fila = jdbc.queryForMap(
            "SELECT centro_id, local_id, nombre, tipo, estado FROM facilityhub.instalaciones WHERE id = ?", id);
        assertThat(fila).containsEntry("centro_id", centro).containsEntry("local_id", local)
            .containsEntry("nombre", nombre).containsEntry("tipo", "PCI").containsEntry("estado", "ACTIVA");
        // Aparece en la lista existente del local y en la del centro.
        mvc.perform(get("/api/locales/{id}/instalaciones", local).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(id.intValue())));
        mvc.perform(get("/api/centros/{id}/instalaciones", centro).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(id.intValue())));
    }

    @Test
    void fh010a_it_02_explotacionRegistraZonaComunSinLocal() throws Exception {
        Long centro = centroDe(idLocal(LOCAL_PROPIO));
        Long id = idCreado(alta(EMAIL_EXPLOTACION, centro,
                Map.of("nombre", nombreSintetico("zc"), "tipo", "ELEVACION", "estado", "FUERA_SERVICIO"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.localId").value(nullValue()))
            .andExpect(jsonPath("$.estado").value("FUERA_SERVICIO")));
        sincronizar();
        assertThat(jdbc.queryForObject("SELECT local_id FROM facilityhub.instalaciones WHERE id = ?", Long.class, id)).isNull();
        // No aparece en ningun local.
        mvc.perform(get("/api/locales/{id}/instalaciones", idLocal(LOCAL_PROPIO)).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
            .andExpect(jsonPath("$[*].id", not(hasItem(id.intValue()))));
    }

    @Test
    void fh010a_it_03_referenciasNoValidasNoCreanNada() throws Exception {
        Long localPropio = idLocal(LOCAL_PROPIO);
        Long centro = centroDe(localPropio);
        Long otroCentro = jdbc.queryForObject(
            "INSERT INTO facilityhub.centros (codigo, nombre, activo) VALUES (?, ?, true) RETURNING id",
            Long.class, nombreSintetico("c"), nombreSintetico("c"));
        Long localOtroCentro = jdbc.queryForObject(
            "INSERT INTO facilityhub.locales (centro_id, codigo, activo) VALUES (?, ?, true) RETURNING id",
            Long.class, otroCentro, nombreSintetico("l"));
        sincronizar();
        int antes = total();

        alta(EMAIL_ADMIN, Long.MAX_VALUE, Map.of("nombre", nombreSintetico("x"), "tipo", "PCI")).andExpect(status().isNotFound());
        alta(EMAIL_ADMIN, centro, Map.of("localId", Long.MAX_VALUE, "nombre", nombreSintetico("x"), "tipo", "PCI"))
            .andExpect(status().isNotFound());
        alta(EMAIL_ADMIN, centro, Map.of("localId", localOtroCentro, "nombre", nombreSintetico("x"), "tipo", "PCI"))
            .andExpect(status().isBadRequest());
        sincronizar();
        assertThat(total()).isEqualTo(antes);
    }

    @Test
    void fh010a_it_04_camposObligatoriosLongitudesYEstadoNoValidos() throws Exception {
        Long centro = centroDe(idLocal(LOCAL_PROPIO));
        int antes = total();
        alta(EMAIL_ADMIN, centro, Map.of("nombre", "   ", "tipo", "PCI")).andExpect(status().isBadRequest());
        alta(EMAIL_ADMIN, centro, Map.of("nombre", nombreSintetico("x"))).andExpect(status().isBadRequest());
        alta(EMAIL_ADMIN, centro, Map.of("nombre", "N".repeat(121), "tipo", "PCI")).andExpect(status().isBadRequest());
        alta(EMAIL_ADMIN, centro, Map.of("nombre", nombreSintetico("x"), "tipo", "T".repeat(81))).andExpect(status().isBadRequest());
        alta(EMAIL_ADMIN, centro, Map.of("nombre", nombreSintetico("x"), "tipo", "PCI", "estado", "ROTA"))
            .andExpect(status().isBadRequest());
        sincronizar();
        assertThat(total()).isEqualTo(antes);
    }

    @Test
    void fh010a_it_05_duplicadoEnLaMismaUbicacionDa409PeroEnOtraSePermite() throws Exception {
        Long local = idLocal(LOCAL_PROPIO);
        Long centro = centroDe(local);
        String nombre = nombreSintetico("dup");
        alta(EMAIL_ADMIN, centro, Map.of("localId", local, "nombre", nombre, "tipo", "PCI")).andExpect(status().isCreated());
        alta(EMAIL_ADMIN, centro, Map.of("localId", local, "nombre", " " + nombre.toUpperCase() + " ", "tipo", "PCI"))
            .andExpect(status().isConflict());
        // Mismo nombre en la zona comun del centro: es otra ubicacion.
        alta(EMAIL_ADMIN, centro, Map.of("nombre", nombre, "tipo", "PCI")).andExpect(status().isCreated());
        sincronizar();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.instalaciones WHERE lower(nombre) = lower(?)",
            Integer.class, nombre)).isEqualTo(2);
    }

    @Test
    void fh010a_it_06_centroOLocalInactivoDa409() throws Exception {
        Long local = idLocal(LOCAL_PROPIO);
        Long centro = centroDe(local);
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE id = ?", local);
        sincronizar();
        alta(EMAIL_ADMIN, centro, Map.of("localId", local, "nombre", nombreSintetico("x"), "tipo", "PCI"))
            .andExpect(status().isConflict());
        jdbc.update("UPDATE facilityhub.centros SET activo = false WHERE id = ?", centro);
        sincronizar();
        alta(EMAIL_ADMIN, centro, Map.of("nombre", nombreSintetico("x"), "tipo", "PCI")).andExpect(status().isConflict());
    }

    @Test
    void fh010a_it_07_inquilinoYProveedorReciben403SinCrearNada() throws Exception {
        Long centro = centroDe(idLocal(LOCAL_PROPIO));
        Long proveedor = jdbc.queryForObject("INSERT INTO facilityhub.proveedores (razon_social, activo) VALUES (?, true) RETURNING id",
            Long.class, nombreSintetico("prov"));
        String emailTecnico = nombreSintetico("tec").toLowerCase() + "@sintetico.test";
        jdbc.update("""
            INSERT INTO facilityhub.usuarios (nombre, email, password_hash, rol_id, proveedor_id)
            VALUES ('P4-tecnico', ?, 'x', (SELECT id FROM facilityhub.roles WHERE codigo = 'PROVEEDOR'), ?)""",
            emailTecnico, proveedor);
        sincronizar();
        int antes = total();
        for (String email : new String[] {EMAIL_INQUILINO, emailTecnico}) {
            alta(email, centro, Map.of("nombre", nombreSintetico("x"), "tipo", "PCI")).andExpect(status().isForbidden());
            mvc.perform(get("/api/centros/{id}/instalaciones", centro).header(HttpHeaders.AUTHORIZATION, bearer(email)))
                .andExpect(status().isForbidden());
        }
        sincronizar();
        assertThat(total()).isEqualTo(antes);
    }

    @Test
    void fh010a_it_08_sinTokenOTokenCaducadoDa401() throws Exception {
        Long centro = centroDe(idLocal(LOCAL_PROPIO));
        String cuerpo = "{\"nombre\":\"P4-x\",\"tipo\":\"PCI\"}";
        mvc.perform(post("/api/centros/{id}/instalaciones", centro).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/centros/{id}/instalaciones", centro).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header(HttpHeaders.AUTHORIZATION, bearerCaducado(EMAIL_ADMIN, "ADMIN")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void fh010a_it_09_elAltaNoCreaObligacionesNiMantenimientos() throws Exception {
        Long local = idLocal(LOCAL_PROPIO);
        Integer obligaciones = jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.obligaciones_mantenimiento", Integer.class);
        Integer mantenimientos = jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.mantenimientos", Integer.class);
        alta(EMAIL_ADMIN, centroDe(local), Map.of("localId", local, "nombre", nombreSintetico("x"), "tipo", "PCI"))
            .andExpect(status().isCreated());
        sincronizar();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.obligaciones_mantenimiento", Integer.class)).isEqualTo(obligaciones);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.mantenimientos", Integer.class)).isEqualTo(mantenimientos);
    }

    private ResultActions alta(String email, Long centroId, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/centros/{id}/instalaciones", centroId)
            .header(HttpHeaders.AUTHORIZATION, bearer(email))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(cuerpo)));
    }

    private static String json(Map<String, Object> cuerpo) {
        StringBuilder sb = new StringBuilder("{");
        cuerpo.forEach((k, v) -> {
            if (sb.length() > 1) sb.append(',');
            sb.append('"').append(k).append("\":");
            sb.append(v instanceof String s ? "\"" + s + "\"" : String.valueOf(v));
        });
        return sb.append('}').toString();
    }

    private static Long idCreado(ResultActions r) throws Exception {
        String cuerpo = r.andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(cuerpo);
        assertThat(m.find()).isTrue();
        return Long.valueOf(m.group(1));
    }

    private Long centroDe(Long localId) {
        return jdbc.queryForObject("SELECT centro_id FROM facilityhub.locales WHERE id = ?", Long.class, localId);
    }

    private int total() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM facilityhub.instalaciones", Integer.class);
    }
}
