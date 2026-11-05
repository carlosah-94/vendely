package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Registro de un cliente en una tienda (identificada por su subdominio). */
public record RegistroClienteRequest(
        @NotBlank(message = "Indica en qué tienda te registras")
        String subdominio,

        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Solo letras, números, punto, guion y guion bajo")
        String usuario,

        @NotBlank @Email(message = "El correo no es válido") @Size(max = 150)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no es válido")
        String correo,

        @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe tener al menos una letra y un número")
        String contrasena,

        @NotBlank @Size(max = 80) String nombres,
        @NotBlank @Size(max = 80) String apellidoPat,
        @Size(max = 80) String apellidoMat,
        @Size(max = 20) String telefono,

        Boolean aceptaMarketing
) {}
