package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Editar mi perfil. El correo no se cambia aquí (es el usuario para entrar y se verificó con un
 * código). razonSocial y telefonoComercial solo se guardan si la cuenta es de un dueño.
 */
public record PerfilRequest(
        @NotBlank(message = "Los nombres son obligatorios") @Size(max = 80) String nombres,
        @NotBlank(message = "El primer apellido es obligatorio") @Size(max = 80) String primerApellido,
        @Size(max = 80) String segundoApellido,

        @Pattern(regexp = "^$|^\\+[1-9][0-9]{6,14}$",
                 message = "El teléfono debe tener formato internacional, por ejemplo +51987654321")
        String telefono,

        @Size(max = 150, message = "La razón social puede tener hasta 150 caracteres") String razonSocial,

        // RUC del dueño (V24): vacío = sin RUC. Su dígito verificador lo revisan el servicio y la base.
        @Pattern(regexp = "^$|^[0-9]{11}$", message = "El RUC tiene 11 dígitos")
        String ruc,

        @Pattern(regexp = "^$|^\\+[1-9][0-9]{6,14}$",
                 message = "El teléfono debe tener formato internacional, por ejemplo +51987654321")
        String telefonoComercial
) {}
