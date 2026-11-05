package curso.fullstack.shopify.dto;

/** Respuesta de registro e inicio de sesión: el token y quién es el usuario. */
public record AuthResponse(
        String token,
        String tipo,            // siempre "Bearer"
        long expiraEnSegundos,
        UsuarioResponse usuario
) {}
