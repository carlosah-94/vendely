package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dirección del cliente al crearlo (issue #9) o al agregar una desde la tienda.
 * El destinatario y el teléfono se toman del propio cliente; el teléfono solo se envía
 * desde la tienda si la cuenta no tiene uno.
 *
 * Dos casos (los revisa ClienteService.ubicar):
 * - País de la tienda (o idPais vacío): se elige el distrito de la lista oficial (idDistrito).
 * - Otro país: se escriben ciudad, región y código postal. Así un cliente de España puede
 *   comprar en una tienda de Perú, si la tienda envía a España.
 */
public record DireccionRequest(
        // Opcional: si no viene, es el país de la tienda. ISO 3166-1, por ejemplo "ES".
        @Pattern(regexp = "^[A-Z]{2}$", message = "El país no es válido")
        String idPais,

        // id_division del distrito (ubigeo en Perú). Obligatorio en el país de la tienda.
        Long idDistrito,

        // Solo para otro país. Mismos largos que la tabla direcciones.
        @Size(max = 100, message = "La ciudad puede tener hasta 100 caracteres") String ciudad,
        @Size(max = 100, message = "La región puede tener hasta 100 caracteres") String region,
        @Size(max = 12, message = "El código postal puede tener hasta 12 caracteres") String codigoPostal,

        @NotBlank(message = "La dirección es obligatoria") @Size(max = 200) String direccion,

        @Size(max = 200) String referencia,

        // Opcional. Formato internacional E.164.
        @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$",
                 message = "El teléfono debe tener formato internacional, por ejemplo +51987654321")
        String telefono
) {}
