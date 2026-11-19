package com.facilityhub.p4;

import com.facilityhub.repository.UsuarioRepository;
import com.facilityhub.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * FH-005A1 / P4 - Base comun de las pruebas integradas.
 *
 * - Contexto Spring completo SIN servidor HTTP (MOCK); MockMvc construido a mano con la cadena real de seguridad.
 * - Destino JDBC, Flyway, DDL, secreto JWT y almacenamiento fijados por @DynamicPropertySource (maxima precedencia)
 *   y validados por P4BarreraEntornoInitializer antes de crear el DataSource.
 * - @Transactional: cada prueba se revierte; las peticiones MockMvc (modo MOCK) se procesan en el hilo de la prueba
 *   y participan en esa transaccion. Los archivos fisicos no se revierten: viven en un directorio temporal propio.
 * - Las clases *P4IT no coinciden con los patrones por defecto de Surefire; solo se ejecutan seleccionandolas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = P4BarreraEntornoInitializer.class)
@Import(P4RelojConfig.class)
@Transactional
public abstract class P4IntegracionBase {
    protected static final String EMAIL_ADMIN = "admin@facilityhub.local";
    protected static final String EMAIL_EXPLOTACION = "explotacion@facilityhub.local";
    protected static final String EMAIL_INQUILINO = "inquilino@facilityhub.local";
    protected static final String LOCAL_PROPIO = "L-101";
    protected static final String LOCAL_AJENO = "L-205";

    @DynamicPropertySource
    static void propiedadesP4(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> P4Entorno.URL_AUTORIZADA);
        registro.add("spring.datasource.username", () -> P4Entorno.USUARIO);
        registro.add("spring.datasource.password", P4Entorno::contrasenaDeSesion);
        registro.add("spring.flyway.enabled", () -> "false");
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registro.add("spring.jpa.properties.hibernate.default_schema", () -> P4Entorno.ESQUEMA);
        registro.add("security.jwt.secret", P4Entorno::secretoJwtSintetico);
        registro.add("facilityhub.storage.root", () -> P4Entorno.directorioAlmacenamiento().toString());
    }

    @Autowired protected WebApplicationContext contextoWeb;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JwtTokenService jwtTokenService;
    @Autowired protected JwtEncoder jwtEncoder;
    @Autowired protected UsuarioRepository usuarioRepository;
    @PersistenceContext protected EntityManager entityManager;

    protected MockMvc mvc;

    // Segunda capa (la primera es la barrera previa al contexto): cada prueba confirma la BD real conectada.
    @BeforeEach
    void verificarBaseYConstruirMockMvc() {
        String actual = jdbc.queryForObject("SELECT current_database()", String.class);
        if (!P4Entorno.BD_AUTORIZADA.equals(actual)) {
            throw new IllegalStateException("P4 abortada: la conexion no apunta a la base de pruebas");
        }
        Integer v4 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM facilityhub.flyway_schema_history WHERE version = '4' AND success", Integer.class);
        if (v4 == null || v4 != 1) {
            throw new IllegalStateException("P4 abortada: la base de pruebas no tiene V4 aplicada una vez");
        }
        Filter seguridad = contextoWeb.getBean("springSecurityFilterChain", Filter.class);
        mvc = MockMvcBuilders.webAppContextSetup(contextoWeb).addFilters(seguridad).build();
    }

    // ---- JWT sinteticos (firmados con el secreto aleatorio de la ejecucion; nunca se imprimen) ----

    protected String bearer(String email) {
        return "Bearer " + usuarioRepository.findByEmailAndActivoTrue(email)
            .map(jwtTokenService::generarToken)
            .orElseThrow(() -> new IllegalStateException("Usuario demo no disponible en la base de pruebas"));
    }

    protected String bearerCaducado(String email, String rol) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("facilityhub")
            .issuedAt(ahora.minus(Duration.ofHours(3)))
            .expiresAt(ahora.minus(Duration.ofHours(2)))
            .subject(email)
            .claim("role", rol)
            .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    protected String bearerFirmaAjena(String email, String rol) {
        byte[] clave = new byte[48];
        new SecureRandom().nextBytes(clave);
        JwtEncoder otro = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(clave, "HmacSHA256")).algorithm(MacAlgorithm.HS256).build();
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("facilityhub").issuedAt(ahora).expiresAt(ahora.plus(Duration.ofMinutes(10)))
            .subject(email).claim("role", rol).build();
        return "Bearer " + otro.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    protected String bearerSujetoInexistente(String rol) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("facilityhub").issuedAt(ahora).expiresAt(ahora.plus(Duration.ofMinutes(10)))
            .subject(P4Entorno.PREFIJO_DATOS + "inexistente@sintetico.test").claim("role", rol).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    // ---- Datos de referencia (solo lectura) ----

    protected Long idLocal(String codigo) {
        return jdbc.queryForObject("SELECT id FROM facilityhub.locales WHERE codigo = ?", Long.class, codigo);
    }

    protected Long idMantenimientoDeLocal(String codigoLocal) {
        return jdbc.queryForObject("""
            SELECT MIN(m.id) FROM facilityhub.mantenimientos m
            JOIN facilityhub.obligaciones_mantenimiento o ON o.id = m.obligacion_id
            JOIN facilityhub.locales l ON l.id = o.local_id
            WHERE l.codigo = ?""", Long.class, codigoLocal);
    }

    protected Long idInquilinoDelUsuario(String email) {
        return jdbc.queryForObject("SELECT inquilino_id FROM facilityhub.usuarios WHERE email = ?", Long.class, email);
    }

    protected Long idInquilinoOcupante(String codigoLocal) {
        return jdbc.queryForObject("""
            SELECT oc.inquilino_id FROM facilityhub.ocupaciones_local oc
            JOIN facilityhub.locales l ON l.id = oc.local_id
            WHERE l.codigo = ? AND oc.activo""", Long.class, codigoLocal);
    }

    // ---- Datos sinteticos (dentro de la transaccion de la prueba; se revierten) ----

    protected String nombreSintetico(String sufijo) {
        return P4Entorno.PREFIJO_DATOS + UUID.randomUUID().toString().substring(0, 8) + "-" + sufijo;
    }

    protected Long insertarDocumento(Long mantenimientoId, String nombre, String estado, String visibilidad, String ruta) {
        return jdbc.queryForObject("""
            INSERT INTO facilityhub.documentos_mantenimiento
              (mantenimiento_id, nombre_original, ruta_archivo, estado_revision, observaciones, visibilidad)
            VALUES (?, ?, ?, ?, 'P4- observacion interna sintetica', ?) RETURNING id""",
            Long.class, mantenimientoId, nombre, ruta, estado, visibilidad);
    }

    // Archivo sintetico en el directorio temporal de la ejecucion (nunca en storage/ real).
    protected Path crearArchivoSintetico(String nombre, byte[] contenido) {
        try {
            Path carpeta = Files.createDirectories(P4Entorno.directorioAlmacenamiento().resolve("p4-sinteticos"));
            return Files.write(carpeta.resolve(nombre), contenido).toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected long archivosEnAlmacenamiento() {
        try (Stream<Path> rutas = Files.walk(P4Entorno.directorioAlmacenamiento())) {
            return rutas.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Vuelca a BD lo pendiente y vacia la cache de JPA, para leer el estado real tras cambios por JDBC o por la API.
    protected void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    protected String valorEnBd(String columna, Long documentoId) {
        return jdbc.queryForObject("SELECT " + columna + " FROM facilityhub.documentos_mantenimiento WHERE id = ?",
            String.class, documentoId);
    }
}
