package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Login;

/** Datos públicos de la cuenta: nunca incluye el hash de la contraseña. */
public record UsuarioResponse(
        Long idLogin,
        String usuario,
        String correo,
        String nombres,
        String primerApellido,
        String segundoApellido,
        String rol,
        Long idTienda
) {
    public static UsuarioResponse desde(Login l) {
        return new UsuarioResponse(l.getIdLogin(), l.getUsuario(), l.getCorreo(), l.getNombres(),
                l.getPrimerApellido(), l.getSegundoApellido(), l.getRol().name(), l.getIdTienda());
    }
}
