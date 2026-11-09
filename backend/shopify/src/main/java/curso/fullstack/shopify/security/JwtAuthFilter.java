package curso.fullstack.shopify.security;

import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.repository.LoginRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Revisa el token en cada petición, como el guardia que mira la pulsera en la puerta.
 * Si el token es válido, deja anotado quién es el usuario para el resto de la petición.
 * Si no hay token o no sirve, no anota a nadie: las rutas protegidas responderán 401.
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final LoginRepository loginRepository;

    public JwtAuthFilter(JwtService jwtService, LoginRepository loginRepository) {
        this.jwtService = jwtService;
        this.loginRepository = loginRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.leerToken(cabecera.substring(7));
                Long idLogin = Long.valueOf(claims.getSubject());
                // Se consulta la cuenta en cada petición: si fue desactivada o cambió
                // su contraseña después de emitir el token, el token deja de servir.
                loginRepository.findById(idLogin)
                        .filter(Login::estaActivo)
                        .filter(l -> tokenPosteriorAlCambio(claims, l))
                        .ifPresent(l -> autenticar(l, request));
            } catch (JwtException | IllegalArgumentException e) {
                // Token vencido, alterado o mal formado: se sigue sin usuario.
                // Se registra el tipo de problema (ExpiredJwtException, SignatureException...), nunca el token.
                log.warn("Token rechazado en {} {}: {}", request.getMethod(), request.getRequestURI(),
                        e.getClass().getSimpleName());
            }
        }
        chain.doFilter(request, response);
    }

    private boolean tokenPosteriorAlCambio(Claims claims, Login login) {
        if (login.getContrasenaCambiadaEn() == null || claims.getIssuedAt() == null) {
            return true;
        }
        long emitidoSeg = claims.getIssuedAt().getTime() / 1000;
        long cambioSeg = login.getContrasenaCambiadaEn().toEpochSecond();
        return emitidoSeg >= cambioSeg;
    }

    private void autenticar(Login login, HttpServletRequest request) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(
                login.getIdLogin(), login.getRol(), login.getIdTienda(), login.getCorreo());
        var permisos = List.of(new SimpleGrantedAuthority("ROLE_" + login.getRol().name()));
        var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, permisos);
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }
}
