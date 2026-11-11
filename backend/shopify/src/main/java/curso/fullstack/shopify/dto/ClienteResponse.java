package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Login;
import java.time.OffsetDateTime;

/**
 * Datos de un cliente para el panel del dueño (issue #9).
 * Nunca incluye el hash de la contraseña: aunque sea un hash, no debe salir del backend.
 */
public record ClienteResponse(
        Long idLogin,
        String usuario,
        String nombres,
        String primerApellido,
        String segundoApellido,
        String correo,
        String telefono,
        String estado,               // "ACTIVO" o "INACTIVO"
        OffsetDateTime fechaCreacion,
        DireccionResponse direccion  // null en la lista (para que sea liviana) o si no tiene
) {
    /**
     * idPais y pais siempre vienen. En el país de la tienda se llena distrito; en otro país,
     * ciudad, region y codigoPostal.
     */
    public record DireccionResponse(Long idDireccion, Long idDistrito, String distrito,
                                    String direccion, String referencia,
                                    String idPais, String pais, String ciudad, String region,
                                    String codigoPostal) {}

    public static ClienteResponse desde(Login l, DireccionResponse direccion) {
        return new ClienteResponse(l.getIdLogin(), l.getUsuario(), l.getNombres(), l.getPrimerApellido(),
                l.getSegundoApellido(), l.getCorreo(), l.getTelefono(),
                l.estaActivo() ? "ACTIVO" : "INACTIVO", l.getFechaCreacion(), direccion);
    }
}
