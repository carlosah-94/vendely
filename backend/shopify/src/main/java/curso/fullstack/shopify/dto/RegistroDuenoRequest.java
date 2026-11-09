package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Registro de un dueño de tienda: crea su cuenta del panel y sus datos de propietario. */
public record RegistroDuenoRequest(
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
        @NotBlank @Size(max = 80) String primerApellido,
        @Size(max = 80) String segundoApellido,

        @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$",
                 message = "El teléfono debe tener formato internacional, por ejemplo +51987654321")
        String telefono,

        @NotNull @Min(1)
        Short tipoDocumento,          // id de tipos_documento: 1 DNI · 2 RUC · 3 CE (Perú) · 4 RUT (Chile)…

        @NotBlank @Size(max = 20)
        String numeroDocumento,

        @Size(max = 150) String razonSocial
) {}
