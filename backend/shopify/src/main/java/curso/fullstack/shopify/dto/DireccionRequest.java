package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dirección del cliente al crearlo (issue #9).
 * El país no se pide: es el de la tienda (la tienda vende dentro de su país).
 * El destinatario y el teléfono tampoco: se toman del propio cliente.
 */
public record DireccionRequest(
        // id_division del distrito (ubigeo en Perú). Se elige de la lista oficial, no se escribe a mano.
        @NotNull(message = "Elige el distrito") Long idDistrito,

        // Mismos largos que la tabla direcciones: si fueran mayores, la base rechazaría el dato.
        @NotBlank(message = "La dirección es obligatoria") @Size(max = 200) String direccion,

        @Size(max = 200) String referencia
) {}
