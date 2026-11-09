// dto/LineaVentaRequest.java
package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LineaVentaRequest(
        @NotNull(message = "Indica el producto (idVariante)") Long idVariante,
        @NotNull @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad) {
}