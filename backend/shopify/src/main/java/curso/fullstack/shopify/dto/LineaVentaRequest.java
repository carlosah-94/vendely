// dto/LineaVentaRequest.java
package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LineaVentaRequest(
        @NotNull(message = "Indica el producto (idVariante)") Long idVariante,
        @NotNull @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad,
        // Respuesta a la pregunta del producto (V19): "Feliz cumpleaños, Ana". Opcional en el JSON;
        // si el producto pregunta algo, el pedido desde la tienda la exige.
        @Size(max = 200, message = "La respuesta puede tener hasta 200 caracteres") String datoCliente) {
}