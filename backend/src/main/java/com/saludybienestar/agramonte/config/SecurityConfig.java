package com.saludybienestar.agramonte.config;

import com.saludybienestar.agramonte.controller.CitaController;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.security.JwtAuthFilter;
import com.saludybienestar.agramonte.security.RateLimitFilter;
import jakarta.servlet.Filter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad de la API: quién entra a cada ruta.
 *
 * <p>Qué datos se devuelven dentro de una ruta no se decide aquí, sino en el
 * controlador o el servicio (ver {@code docs/ARQUITECTURA.md}).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String PACIENTE = Rol.PACIENTE.name();
    private static final String MEDICO = Rol.MEDICO.name();
    private static final String ADMIN = Rol.ADMIN.name();

    /**
     * Orígenes del frontend en desarrollo (docker compose: web en :80, API en :8080,
     * más los servidores estáticos habituales). En producción la propia app sirve
     * el frontend, mismo origen, y CORS no entra en juego.
     */
    private static final List<String> ORIGENES_DESARROLLO = List.of(
            "http://localhost",
            "http://localhost:80",
            "http://localhost:3000",
            "http://localhost:3456",
            "http://localhost:5500",
            "http://127.0.0.1",
            "http://127.0.0.1:80",
            "http://127.0.0.1:3456",
            "http://127.0.0.1:5500");

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Solo la API y actuator. Los estáticos del frontend se sirven fuera
                // de esta cadena, públicos, porque la app sirve web y API juntas.
                .securityMatcher("/api/**", "/actuator/**")
                // CSRF desactivado a propósito. El JWT viaja en la cabecera
                // Authorization, que el navegador no añade solo a una petición de otra
                // web, y no hay cookies ni sesión. CodeQL lo marca igual
                // (java/spring-disabled-csrf-protection): la alerta está revisada.
                // Si el token pasara algún día a una cookie, hay que reactivarlo.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(SecurityConfig::reglasDeAcceso)
                // Sin credenciales, 401. Sin este entry point Spring respondería 403,
                // que significa «identificado pero sin permiso». A quien le falta el
                // rol le sigue llegando 403.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // El límite va primero: una petición que se va a rechazar no debe
                // gastar el BCrypt del login ni la consulta del usuario del JWT.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, RateLimitFilter.class)
                .build();
    }

    /**
     * Cada ruta de la API, con lo mínimo que exige. Lo que no está en la lista se
     * deniega: un endpoint nuevo no queda abierto por olvidarse de declararlo aquí.
     */
    private static void reglasDeAcceso(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // Públicas
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro",
                        "/api/auth/recuperar", "/api/auth/activar").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/medicos", "/api/centros",
                        "/api/disponibilidad").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/contact", "/api/citas/reserva-publica").permitAll()
                // Lo llama Telegram, no un usuario: no puede traer JWT. Se autentica
                // con el secreto del webhook dentro del controlador.
                .requestMatchers(HttpMethod.POST, "/api/telegram/webhook").permitAll()
                // Citas de invitado. Públicas para la cadena (no hay JWT), pero no
                // anónimas: sin el token de la cita el servicio responde 404.
                .requestMatchers(HttpMethod.GET, "/api/citas/publica/*").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/citas/publica/*").permitAll()

                // Paciente
                .requestMatchers(HttpMethod.GET, "/api/citas/mias").hasRole(PACIENTE)
                .requestMatchers(HttpMethod.POST, "/api/citas").hasAnyRole(PACIENTE, ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/citas/*").hasAnyRole(PACIENTE, ADMIN)

                // Médico. La agenda expone nombre, email, teléfono y citas de los
                // pacientes: nunca puede ser pública.
                .requestMatchers(HttpMethod.GET, "/api/estadisticas").hasAnyRole(MEDICO, ADMIN)
                .requestMatchers("/api/citas/agenda/**").hasAnyRole(MEDICO, ADMIN)

                // Cualquier cuenta con sesión
                .requestMatchers("/api/telegram/vinculacion").authenticated()

                .anyRequest().denyAll();
    }

    /**
     * Los dos filtros son {@code @Component}, y Spring Boot registra cualquier bean
     * {@link Filter} también en el contenedor de servlets. Correrían dos veces: dentro
     * de la cadena de seguridad y fuera de ella, también para los estáticos. Solo deben
     * hacerlo dentro de la cadena, así que se desactiva ese segundo registro.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> registroJwtAuthFilter() {
        return sinRegistroEnServlet(jwtAuthFilter);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> registroRateLimitFilter() {
        return sinRegistroEnServlet(rateLimitFilter);
    }

    private static <T extends Filter> FilterRegistrationBean<T> sinRegistroEnServlet(T filtro) {
        FilterRegistrationBean<T> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(ORIGENES_DESARROLLO);
        // La API no tiene PUT ni PATCH.
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        // Solo las cabeceras que manda el frontend (js/modules/api-client.js).
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, CitaController.CABECERA_TOKEN));
        // La autenticación va en la cabecera, no en cookies: sin credenciales CORS.
        config.setAllowCredentials(false);
        // Cachea el preflight una hora para no repetir el OPTIONS en cada petición.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
