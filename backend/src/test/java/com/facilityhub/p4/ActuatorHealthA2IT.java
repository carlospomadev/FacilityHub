package com.facilityhub.p4;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-005A2-E - /actuator/health solo expone el estado agregado (show-details y show-components: never),
 * tanto a anonimos como a usuarios autenticados de gestion. Cadena real de Spring Security y JWT sinteticos.
 * Infraestructura P4 sin cambios (destino facilityhub_fh005_p3, Flyway desactivado, validate, rollback).
 * PREPARADA EN A2-E, NO EJECUTADA. El sufijo *A2IT no coincide con los patrones por defecto de Surefire.
 */
class ActuatorHealthA2IT extends P4IntegracionBase {
    // Estados estandar de Spring Boot; no se acopla la prueba a UP para no depender de la salud real.
    private static final String[] ESTADOS = {"UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN"};

    @Test
    void a2e_01_anonimoSoloRecibeElEstadoAgregado() throws Exception {
        assertSoloEstado(consultar(get("/actuator/health")));
    }

    @Test
    void a2e_02_gestionAutenticadaTampocoRecibeDetalles() throws Exception {
        for (String email : new String[] {EMAIL_ADMIN, EMAIL_EXPLOTACION}) {
            assertSoloEstado(consultar(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(email))));
        }
    }

    @Test
    void a2e_03_inquilinoAutenticadoTampocoRecibeDetalles() throws Exception {
        assertSoloEstado(consultar(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO))));
    }

    @Test
    void a2e_04_subrutasDeComponentesNoExponenDetalles() throws Exception {
        // Anonimo: la subruta no es publica en SecurityConfig (solo /actuator/health exacto).
        mvc.perform(get("/actuator/health/db")).andExpect(status().isUnauthorized());
        // Autenticado: sea cual sea el codigo, el cuerpo no puede revelar componentes ni detalles.
        for (String componente : new String[] {"db", "diskSpace", "ping"}) {
            MvcResult resultado = consultar(get("/actuator/health/{c}", componente)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)));
            assertSinInformacionInterna(resultado.getResponse().getContentAsString());
        }
    }

    // ---- Apoyo ----

    private MvcResult consultar(MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion).andReturn();
    }

    private void assertSoloEstado(MvcResult resultado) throws Exception {
        int codigo = resultado.getResponse().getStatus();
        String cuerpo = resultado.getResponse().getContentAsString();
        // 200 si UP; 503 si el estado agregado fuese DOWN/OUT_OF_SERVICE (comportamiento estandar).
        assertThat(codigo).isIn(200, 503);
        Map<String, Object> json = JsonPath.read(cuerpo, "$");
        assertThat(json.keySet()).containsExactly("status");
        assertThat(String.valueOf(json.get("status"))).isIn((Object[]) ESTADOS);
        assertSinInformacionInterna(cuerpo);
    }

    private void assertSinInformacionInterna(String cuerpo) {
        assertThat(cuerpo).doesNotContain(
            "components", "details", "database", "db\"", "diskSpace", "validationQuery", "PostgreSQL",
            "jdbc:", P4Entorno.BD_AUTORIZADA, "facilityhub_", "path", "total", "free", "threshold",
            "password", "Bearer", "exception", "error\"", "trace",
            P4Entorno.directorioAlmacenamiento().toString(), System.getProperty("java.io.tmpdir"), System.getProperty("user.dir"));
    }
}
