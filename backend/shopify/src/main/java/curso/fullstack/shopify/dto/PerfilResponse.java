package curso.fullstack.shopify.dto;

import java.time.OffsetDateTime;

/**
 * Mi perfil (panel → Mi perfil): los datos de la cuenta que inició sesión.
 * negocio: solo para el dueño (sus datos de propietario); null para empleados y el superadmin.
 * Nunca incluye la contraseña ni el secreto de la doble autenticación.
 */
public record PerfilResponse(
        Long idLogin,
        String usuario,
        String correo,
        String nombres,
        String primerApellido,
        String segundoApellido,
        String telefono,
        String rol,
        OffsetDateTime fechaCreacion,
        OffsetDateTime contrasenaCambiadaEn,   // null = nunca la cambió desde que creó la cuenta
        boolean dobleFactor,
        Negocio negocio
) {
    /** tipoDocumento: "RUC", "DNI", "RUT"…; el documento no se edita (identifica al dueño). */
    public record Negocio(String tipoDocumento, String nombreDocumento, String numeroDocumento,
                          String razonSocial, String telefonoComercial) {}
}
