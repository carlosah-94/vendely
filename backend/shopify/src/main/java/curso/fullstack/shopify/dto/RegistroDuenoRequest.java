package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
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
        @NotBlank @Size(max = 80) String apellidoPat,
        @Size(max = 80) String apellidoMat,
        @Size(max = 20) String telefono,

        @NotNull @Min(1) @Max(3)
        Short tipoDocumento,          // 1 DNI · 2 RUC · 3 carné de extranjería

        @NotBlank @Size(max = 12)
        String numeroDocumento,

        @Size(max = 150) String razonSocial
) {}
