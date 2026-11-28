package com.facilityhub.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
        FacilityHubUserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder) {

    DaoAuthenticationProvider authenticationProvider =
            new DaoAuthenticationProvider(userDetailsService);

    authenticationProvider.setPasswordEncoder(passwordEncoder);

    return new ProviderManager(authenticationProvider);
}

    @Bean
    SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET debe contener al menos 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey) {
        return NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                // FH-006: PROVEEDOR solo accede a sus trabajos; nunca a centros, locales, obligaciones ni mantenimientos.
                .requestMatchers(HttpMethod.GET, "/api/proveedor/trabajos", "/api/proveedor/trabajos/*")
                    .hasRole("PROVEEDOR")
                // FH-006 / P6-C1: intervenciones. Validar y la bandeja, solo gestion; el resto, gestion o el PROVEEDOR
                // (permisos finos por recurso en el servicio). Van antes de las reglas generales de /api/mantenimientos.
                .requestMatchers(HttpMethod.POST, "/api/intervenciones/*/validacion")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.GET, "/api/intervenciones/pendientes")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.GET, "/api/mantenimientos/*/intervenciones", "/api/intervenciones/*",
                    "/api/intervenciones/*/documentos/*/archivo")
                    .hasAnyRole("ADMIN", "EXPLOTACION", "PROVEEDOR")
                .requestMatchers(HttpMethod.POST, "/api/mantenimientos/*/intervenciones", "/api/intervenciones/*/documentos",
                    "/api/intervenciones/*/envio")
                    .hasAnyRole("ADMIN", "EXPLOTACION", "PROVEEDOR")
                .requestMatchers(HttpMethod.PATCH, "/api/intervenciones/*")
                    .hasAnyRole("ADMIN", "EXPLOTACION", "PROVEEDOR")
                // FH-006 / P6-D1: alertas propias (el servicio filtra por el usuario de la sesion).
                .requestMatchers(HttpMethod.GET, "/api/alertas", "/api/alertas/no-leidas")
                    .hasAnyRole("ADMIN", "EXPLOTACION", "INQUILINO", "PROVEEDOR")
                .requestMatchers(HttpMethod.PATCH, "/api/alertas/*/leida")
                    .hasAnyRole("ADMIN", "EXPLOTACION", "INQUILINO", "PROVEEDOR")
                // FH-006 / P6-C2: area del INQUILINO (ocupacion y responsabilidad se comprueban en los servicios).
                // FH-006 / P6-D2: ejecucion manual de recordatorios.
                .requestMatchers(HttpMethod.POST, "/api/admin/recordatorios/ejecutar")
                .hasRole("ADMIN")
                // FH-006 / P6-D3: panel de gestion, solo lectura (otros metodos caen en denyAll).
                .requestMatchers(HttpMethod.GET, "/api/gestion/**")
                    .hasAnyRole("ADMIN", "EXPLOTACION")

                .requestMatchers("/api/inquilino/**")
                    .hasRole("INQUILINO")
                // Declaraciones de proveedor: revision por gestion. Solicitudes: consulta gestion, resolucion ADMIN.
                .requestMatchers(HttpMethod.GET, "/api/declaraciones-proveedor/pendientes")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.PATCH, "/api/declaraciones-proveedor/*/revision")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.GET, "/api/solicitudes-proveedor")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.PATCH, "/api/solicitudes-proveedor/*")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/proveedores", "/api/proveedores/*")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers("/api/proveedores/**")
                    .hasRole("ADMIN")
                // FH-013 / RF-03 (D1): gestion de centros. Consulta solo gestion; alta, edicion y activacion solo ADMIN.
                .requestMatchers(HttpMethod.GET, "/api/centros/gestion", "/api/centros/gestion/*")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.POST, "/api/centros")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/centros/*", "/api/centros/*/activo")
                    .hasRole("ADMIN")
                // FH-014 / RF-04 (D1): gestion de locales. Antes del GET general de centros y locales, que admite INQUILINO.
                .requestMatchers(HttpMethod.GET, "/api/centros/*/locales/gestion", "/api/locales/gestion/*")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.POST, "/api/centros/*/locales")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/locales/*", "/api/locales/*/activo")
                    .hasRole("ADMIN")
                // FH-015 / RF-05 (D1): gestion de inquilinos (/api/inquilinos, distinto del area /api/inquilino/** del rol
                // INQUILINO). Consulta solo gestion; alta, edicion y activacion solo ADMIN. Cualquier otra ruta: denegada.
                .requestMatchers(HttpMethod.GET, "/api/inquilinos/gestion", "/api/inquilinos/gestion/*")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.POST, "/api/inquilinos")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/inquilinos/*", "/api/inquilinos/*/activo")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/centros/**", "/api/locales/**", "/api/obligaciones/**")
                    .hasAnyRole("ADMIN", "EXPLOTACION", "INQUILINO")
                .requestMatchers(HttpMethod.POST, "/api/obligaciones/**")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                // FH-010A / RF-07: alta de instalaciones solo gestion (el servicio vuelve a comprobarlo).
                .requestMatchers(HttpMethod.POST, "/api/centros/*/instalaciones")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                // FH-010B / RF-09: alta de obligaciones y catalogo de tipos (solo lectura), solo gestion.
                .requestMatchers(HttpMethod.POST, "/api/centros/*/obligaciones")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.GET, "/api/tipos-mantenimiento", "/api/tipos-mantenimiento/**")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                // FH-012 / RF-08 (D1): alta, edicion y activacion del catalogo solo ADMIN (el servicio vuelve a comprobarlo).
                .requestMatchers(HttpMethod.POST, "/api/tipos-mantenimiento")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/tipos-mantenimiento/*", "/api/tipos-mantenimiento/*/activo")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/obligaciones/*/responsable")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.GET, "/api/mantenimientos/**", "/api/documentos/*/archivo")
                    .hasAnyRole("ADMIN", "EXPLOTACION", "INQUILINO")
                .requestMatchers(HttpMethod.POST, "/api/mantenimientos/*/documentos")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers(HttpMethod.PATCH, "/api/mantenimientos/**", "/api/documentos/**")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                .requestMatchers("/api/mantenimientos/**", "/api/documentos/**")
                    .hasAnyRole("ADMIN", "EXPLOTACION")
                // FH-006: cualquier otra ruta de API queda denegada por defecto.
                .requestMatchers("/api/**").denyAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
