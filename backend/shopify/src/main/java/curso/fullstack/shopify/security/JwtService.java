package curso.fullstack.shopify.security;

import curso.fullstack.shopify.entity.Login;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Crea y lee los tokens JWT.
 * Un JWT es como una pulsera de evento: el backend la entrega al iniciar sesión,
 * lleva escrito quién eres y tu rol, y está firmada con una clave secreta que solo
 * conoce el backend. Si alguien cambia algo de la pulsera, la firma deja de coincidir.
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final long expiracionMs;

    public JwtService(@Value("${app.jwt.secret}") String secreto,
                      @Value("${app.jwt.expiration}") long expiracionMs) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);   // firma HS256
        this.expiracionMs = expiracionMs;
    }

    public String generarToken(Login login) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(String.valueOf(login.getIdLogin()))
                .claim("rol", login.getRol().name())
                .claim("tienda", login.getIdTienda())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expiracionMs))
                .signWith(clave)
                .compact();
    }

    /** Verifica la firma y la fecha de vencimiento. Lanza JwtException si el token no sirve. */
    public Claims leerToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpiracionSegundos() {
        return expiracionMs / 1000;
    }
}
