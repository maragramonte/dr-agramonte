package com.saludybienestar.agramonte.config;

import com.saludybienestar.agramonte.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Esta cadena de seguridad solo cubre la API y actuator.
                // Los archivos estáticos del frontend (/, *.html, css, js...) quedan
                // fuera y se sirven públicamente, necesario al servir web + API juntos.
                .securityMatcher("/api/**", "/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                // Usamos el método local unificado
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Rutas Públicas
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/medicos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/centros").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/disponibilidad/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/citas/reserva-publica").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/citas/por-email").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/citas/publica/**").permitAll()
                        // Lo llama Telegram, no un usuario: no puede presentar JWT.
                        .requestMatchers(HttpMethod.POST, "/api/telegram/webhook").permitAll()
                        // Permisos de Pacientes
                        .requestMatchers(HttpMethod.POST, "/api/citas").hasAnyRole("PACIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/citas").hasAnyRole("PACIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/citas/*").hasAnyRole("PACIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/citas/mias").hasRole("PACIENTE")
                        // Permisos de Médicos y Administradores
                        .requestMatchers(HttpMethod.GET, "/api/estadisticas").hasAnyRole("MEDICO", "ADMIN")
                        // Cubre /agenda/pacientes y /agenda/reservas: exponen datos personales
                        // (nombre, email, teléfono y citas), así que nunca deben ser públicos.
                        .requestMatchers("/api/citas/agenda/**").hasAnyRole("MEDICO", "ADMIN")
                        .requestMatchers("/api/historial/**").hasAnyRole("MEDICO", "ADMIN")
                        .requestMatchers("/api/alertas/**").hasAnyRole("MEDICO", "ADMIN")
                        // Solo Administradores
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Bloqueo del resto de endpoints
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // CORS Unificado y Optimizado para evitar errores de compilación con tipos primitivos
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Desarrollo (docker compose: web en :80, API en :8080) y dominio del VPS. En producción
        // el frontend lo sirve la propia app, mismo origen, y CORS apenas entra en juego.
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost",
                "http://localhost:80",
                "http://127.0.0.1",
                "http://127.0.0.1:80",
                "http://localhost:3000",
                "http://localhost:5500",
                "http://localhost:3456",
                "http://127.0.0.1:5500",
                "http://127.0.0.1:3456",
                "https://www.dragramonte.com",
                "https://dragramonte.com"
        ));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Collections.singletonList("*"));

        // La autenticación viaja en la cabecera Authorization: Bearer (no cookies),
        // así que no necesitamos credenciales CORS. Mantenerlo en false = menor superficie.
        config.setAllowCredentials(false);

        // Optimización: Evita peticiones OPTIONS duplicadas cacheando la respuesta preflight 1 hora
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}