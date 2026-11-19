package com.facilityhub.p4;

import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * FH-005A1 / P4 - Constantes y comprobaciones del UNICO entorno autorizado para las pruebas integradas.
 * Ninguna comprobacion usa subcadenas: el destino se valida por igualdad exacta.
 * Nada de esta clase abre conexiones ni lee la contrasena salvo cuando Spring resuelve la propiedad.
 */
public final class P4Entorno {
    public static final String BD_AUTORIZADA = "facilityhub_fh005_p3";
    public static final String URL_AUTORIZADA = "jdbc:postgresql://localhost:5432/facilityhub_fh005_p3";
    public static final String URL_PROHIBIDA = "jdbc:postgresql://localhost:5432/facilityhub";
    public static final String USUARIO = "facilityhub";
    public static final String ESQUEMA = "facilityhub";
    public static final String PROPIEDAD_AUTORIZACION = "p4.autorizado";
    public static final String VARIABLE_CONTRASENA = "P4_DB_PASSWORD";
    public static final String PREFIJO_DATOS = "P4-";
    private static final String PREFIJO_DIRECTORIO = "fh005-p4-";

    private static final String SECRETO_JWT = generarSecretoJwt();
    private static Path directorioAlmacenamiento;

    private P4Entorno() {}

    // Exigido antes de cualquier otra cosa: sin -Dp4.autorizado=facilityhub_fh005_p3 no se arranca nada.
    public static void exigirAutorizacionExplicita() {
        if (!BD_AUTORIZADA.equals(System.getProperty(PROPIEDAD_AUTORIZACION))) {
            throw new IllegalStateException("P4 no autorizada: falta -D" + PROPIEDAD_AUTORIZACION + "=" + BD_AUTORIZADA);
        }
    }

    // Validacion del Environment efectivo (todas las fuentes de propiedades ya resueltas).
    public static void validarEntornoEfectivo(Environment entorno) {
        exigirAutorizacionExplicita();
        exigirIgual(entorno, PROPIEDAD_AUTORIZACION, BD_AUTORIZADA);
        validarUrl(entorno.getProperty("spring.datasource.url"));
        String urlHikari = entorno.getProperty("spring.datasource.hikari.jdbc-url");
        if (urlHikari != null) {
            validarUrl(urlHikari);
        }
        if (entorno.getProperty("spring.datasource.jndi-name") != null) {
            throw new IllegalStateException("P4 abortada: no se admite DataSource JNDI");
        }
        exigirIgual(entorno, "spring.datasource.username", USUARIO);
        exigirIgual(entorno, "spring.flyway.enabled", "false");
        exigirIgual(entorno, "spring.jpa.hibernate.ddl-auto", "validate");
        exigirIgual(entorno, "spring.jpa.properties.hibernate.default_schema", ESQUEMA);
        validarDirectorioAlmacenamiento(entorno.getProperty("facilityhub.storage.root"));
        // Solo se comprueba que la variable de sesion existe; su valor no se lee ni se registra aqui.
        if (System.getenv(VARIABLE_CONTRASENA) == null) {
            throw new IllegalStateException("P4 abortada: falta la variable de sesion " + VARIABLE_CONTRASENA);
        }
    }

    public static void validarUrl(String url) {
        if (!URL_AUTORIZADA.equals(url)) {
            throw new IllegalStateException("P4 abortada: la URL JDBC efectiva no es exactamente la autorizada");
        }
        if (URL_PROHIBIDA.equals(url)) {
            throw new IllegalStateException("P4 abortada: URL de la base original");
        }
    }

    private static void exigirIgual(Environment entorno, String clave, String esperado) {
        String valor = entorno.getProperty(clave);
        if (!esperado.equals(valor)) {
            throw new IllegalStateException("P4 abortada: la propiedad " + clave + " no tiene el valor exigido");
        }
    }

    private static void validarDirectorioAlmacenamiento(String raiz) {
        if (raiz == null) {
            throw new IllegalStateException("P4 abortada: almacenamiento no configurado");
        }
        Path ruta = Paths.get(raiz).toAbsolutePath().normalize();
        Path temporal = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!ruta.startsWith(temporal) || !ruta.getFileName().toString().startsWith(PREFIJO_DIRECTORIO)) {
            throw new IllegalStateException("P4 abortada: el almacenamiento no es el directorio temporal de pruebas");
        }
    }

    // Se resuelve solo cuando Spring crea el DataSource; sin valor por defecto.
    public static String contrasenaDeSesion() {
        String valor = System.getenv(VARIABLE_CONTRASENA);
        if (valor == null || valor.isEmpty()) {
            throw new IllegalStateException("P4 abortada: falta la variable de sesion " + VARIABLE_CONTRASENA);
        }
        return valor;
    }

    public static String secretoJwtSintetico() {
        return SECRETO_JWT;
    }

    // Directorio temporal nuevo, exclusivo de la ejecucion; se borra al terminar la JVM.
    public static synchronized Path directorioAlmacenamiento() {
        if (directorioAlmacenamiento == null) {
            try {
                directorioAlmacenamiento = Files.createTempDirectory(PREFIJO_DIRECTORIO).toAbsolutePath().normalize();
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo crear el directorio temporal de P4", e);
            }
            Path creado = directorioAlmacenamiento;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> borrarDirectorioTemporal(creado)));
        }
        return directorioAlmacenamiento;
    }

    // Solo borra el directorio creado por esta clase y comprobado de nuevo antes de borrar.
    static void borrarDirectorioTemporal(Path directorio) {
        Path temporal = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (directorio == null || !directorio.startsWith(temporal)
                || !directorio.getFileName().toString().startsWith(PREFIJO_DIRECTORIO) || !Files.isDirectory(directorio)) {
            return;
        }
        try (Stream<Path> rutas = Files.walk(directorio)) {
            rutas.sorted(Comparator.reverseOrder()).forEach(ruta -> {
                try {
                    Files.deleteIfExists(ruta);
                } catch (IOException ignorada) {
                    // Limpieza de mejor esfuerzo de un directorio temporal sintetico.
                }
            });
        } catch (IOException ignorada) {
            // Idem.
        }
    }

    private static String generarSecretoJwt() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
