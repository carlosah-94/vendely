package curso.fullstack.shopify.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import curso.fullstack.shopify.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Reglas de acceso de toda la API. Es el "mapa del edificio": qué puertas están
 * abiertas para todos y qué puertas necesitan cierta pulsera (rol).
 *
 * Convención de rutas para los demás módulos:
 *   /api/auth/**     registro e inicio de sesión (abiertas)
 *   /api/public/**   catálogo de las tiendas (GET abierto para visitantes)
 *   /api/panel/**    panel del dueño de tienda (ADMIN_TIENDA)
 *   /api/cliente/**  cuenta, carrito y pedidos del cliente (CLIENTE)
 *   /api/admin/**    administración de Vendely (SUPERADMIN)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // permite usar @PreAuthorize en los controllers
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.origenes:http://localhost:5173,http://localhost:5174}")
    private List<String> origenesPermitidos;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Sin sesiones ni cookies: cada petición trae su token. Por eso CSRF no aplica.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/registro/**", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/panel/**").hasRole("ADMIN_TIENDA")
                .requestMatchers("/api/cliente/**").hasRole("CLIENTE")
                .requestMatchers("/api/admin/**").hasRole("SUPERADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        escribirError(req, res, HttpStatus.UNAUTHORIZED, "Debes iniciar sesión"))
                .accessDeniedHandler((req, res, ex) ->
                        escribirError(req, res, HttpStatus.FORBIDDEN, "No tienes permiso para esta acción")))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * El filtro JWT es un @Component; sin esto Spring Boot también lo registraría
     * como filtro general del servidor y podría ejecutarse fuera de la seguridad.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> registroFiltroJwt(JwtAuthFilter filtro) {
        FilterRegistrationBean<JwtAuthFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    /** BCrypt: guarda la contraseña "triturada" con sal; no se puede revertir. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CORS: solo el frontend de Vendely puede llamar a la API desde el navegador. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenesPermitidos);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void escribirError(HttpServletRequest req, HttpServletResponse res,
                               HttpStatus status, String mensaje) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        ApiError error = new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(),
                mensaje, req.getRequestURI(), null);
        objectMapper.writeValue(res.getOutputStream(), error);
    }
}
