package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para crear una tienda desde el panel del dueño. */
public record TiendaCrearRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 150)
        String nombre,

        @NotBlank(message = "El subdominio es obligatorio") @Size(min = 3, max = 63, message = "El subdominio debe tener entre 3 y 63 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$",
                 message = "Solo letras, números y guiones; no puede empezar ni terminar con guion")
        String subdominio,

        @Size(max = 2000) String descripcion,

        @NotBlank(message = "El correo de contacto es obligatorio") @Email(message = "El correo no es válido") @Size(max = 150)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no es válido")
        String correoContacto,

        @Size(max = 500) @Pattern(regexp = "^https?://.+", message = "El logo debe ser una dirección http o https")
        String logoUrl,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe tener el formato #RRGGBB, por ejemplo #1D4ED8")
        String colorPrincipal,

        Boolean permiteInvitados,

        // País donde vende (ISO 3166-1). Opcional: si no viene, Perú. Define moneda, impuesto y
        // la lista de ubicaciones, y no se cambia después (T8). Solo países habilitados (T12).
        @Pattern(regexp = "^[A-Z]{2}$", message = "El país no es válido")
        String idPais
) {}
