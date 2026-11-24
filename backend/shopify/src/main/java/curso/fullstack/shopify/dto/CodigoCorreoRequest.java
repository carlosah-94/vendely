package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pedir el código de verificación para un correo, antes de crear la cuenta.
 * Sin subdominio, para la cuenta del dueño; con subdominio, para la de un cliente en esa tienda.
 */
public record CodigoCorreoRequest(
        @NotBlank @Email(message = "El correo no es válido") @Size(max = 150)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no es válido")
        String correo,

        @Size(max = 63) String subdominio
) {}
