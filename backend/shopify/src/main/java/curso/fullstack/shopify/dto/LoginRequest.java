package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Inicio de sesión.
 * - Panel (dueño o superadmin): subdominio vacío.
 * - Cliente: subdominio de la tienda donde compra (ej. "zapatillas-carlos"),
 *   porque el mismo correo puede estar registrado en varias tiendas.
 */
public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no es válido")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        String contrasena,

        String subdominio,

        // Solo si la cuenta tiene la doble autenticacion activa: el codigo de 6 digitos de la app.
        // Sin el, esas cuentas reciben 401 con campos.codigo = "requerido".
        String codigo
) {}
