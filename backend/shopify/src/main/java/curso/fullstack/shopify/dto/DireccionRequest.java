package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dirección del cliente al crearlo (issue #9) o al agregar una desde la tienda.
 * El país no se pide: es el de la tienda (la tienda vende dentro de su país).
 * El destinatario y el teléfono se toman del propio cliente; el teléfono solo se envía
 * desde la tienda si la cuenta no tiene uno.
 */
public record DireccionRequest(
        // id_division del distrito (ubigeo en Perú). Se elige de la lista oficial, no se escribe a mano.
        @NotNull(message = "Elige el distrito") Long idDistrito,

        // Mismos largos que la tabla direcciones: si fueran mayores, la base rechazaría el dato.
        @NotBlank(message = "La dirección es obligatoria") @Size(max = 200) String direccion,

        @Size(max = 200) String referencia,

        // Opcional. Formato internacional E.164.
        @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$",
                 message = "El teléfono debe tener formato internacional, por ejemplo +51987654321")
        String telefono
) {}
