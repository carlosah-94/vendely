package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de una tienda. El subdominio no se cambia: es la dirección
 * que ya conocen los clientes y con la que inician sesión.
 */
public record TiendaActualizarRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 150)
        String nombre,

        @Size(max = 2000) String descripcion,

        @NotBlank(message = "El correo de contacto es obligatorio") @Email(message = "El correo no es válido") @Size(max = 150)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no es válido")
        String correoContacto,

        @Size(max = 500) @Pattern(regexp = "^https?://.+", message = "El logo debe ser una dirección http o https")
        String logoUrl,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe tener el formato #RRGGBB, por ejemplo #1D4ED8")
        String colorPrincipal,

        Boolean permiteInvitados
) {}
