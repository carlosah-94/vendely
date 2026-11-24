package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * "Olvidé mi contraseña", paso 2: el código que llegó al correo y la contraseña nueva.
 * Sin subdominio es una cuenta del panel (dueño, empleado o superadmin); con subdominio, la de un
 * cliente en esa tienda.
 */
public record RestablecerContrasenaRequest(
        @NotBlank @Email(message = "El correo no es válido") @Size(max = 150) String correo,

        @Size(max = 63) String subdominio,

        @NotBlank(message = "Escribe el código que te enviamos a tu correo")
        @Pattern(regexp = "^[0-9]{6}$", message = "El código tiene 6 dígitos")
        String codigo,

        @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe tener al menos una letra y un número")
        String contrasena
) {}
