package curso.fullstack.shopify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de un cliente desde el panel del dueño (issue #9, validaciones RF1.1.1).
 * Se valida aquí (Bean Validation) para responder 400 con el campo exacto que está mal,
 * antes de llegar a la base de datos.
 */
public record ClienteCrearRequest(
        // Opcional: la rúbrica no lo pide en el formulario. Si no viene, el service lo genera
        // a partir del correo. Si viene, mismas reglas que en el registro público.
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Solo letras, números, punto, guion y guion bajo")
        String usuario,

        // RF1.1.1 pide 3 a 100, pero en la base son VARCHAR(80): con más de 80 la base daría error.
        @NotBlank @Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres") String nombres,
        @NotBlank @Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres") String primerApellido,
        @Size(max = 80) String segundoApellido,

        // Correo válido; que sea ÚNICO en la tienda lo comprueba el service (necesita la base).
        @NotBlank @Email(message = "El correo no es válido") @Size(max = 150)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no es válido")
        String correo,

        // RF1.1.1: al menos 6 caracteres. Se guardará como hash BCrypt (72 es el máximo que BCrypt usa).
        @NotBlank @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        String contrasena,

        // Obligatorio y en formato internacional, igual que el CHECK de la base (+51987654321).
        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$",
                 message = "El teléfono debe tener formato internacional, por ejemplo +51987654321")
        String telefono,

        // @Valid hace que también se validen los campos de la dirección.
        @NotNull(message = "La dirección es obligatoria") @Valid DireccionRequest direccion
) {}
