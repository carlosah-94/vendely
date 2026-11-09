package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edición de un cliente (issue #9). Mismas reglas que al crear, con dos diferencias:
 * - contrasena es OPCIONAL: si no viene (null), se conserva la actual; si viene, se hashea de nuevo.
 * - usuario y dirección no se editan aquí: el usuario identifica la cuenta, y una dirección
 *   usada en un pedido no se puede modificar (la base lo impide para no alterar ventas pasadas).
 */
public record ClienteActualizarRequest(
        @NotBlank @Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres") String nombres,
        @NotBlank @Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres") String primerApellido,
        @Size(max = 80) String segundoApellido,

        @NotBlank @Email(message = "El correo no es válido") @Size(max = 150)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no es válido")
        String correo,

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$",
                 message = "El teléfono debe tener formato internacional, por ejemplo +51987654321")
        String telefono,

        // Opcional: @Size solo se revisa si viene un valor (null pasa la validación).
        @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        String contrasena
) {}
