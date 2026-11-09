// dto/VentaRequest.java
package curso.fullstack.shopify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record VentaRequest(
        @NotNull(message = "Indica el cliente") Long idLogin,
        @NotNull(message = "Indica la dirección de envío") Long idDireccionEnvio,
        @NotEmpty(message = "La venta debe tener al menos un producto") @Valid List<LineaVentaRequest> lineas) {
}