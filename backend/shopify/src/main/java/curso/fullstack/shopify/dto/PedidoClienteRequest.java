package curso.fullstack.shopify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Pedido que hace un cliente desde la tienda. No lleva idLogin ni idTienda:
 * salen del token, para que nadie compre "a nombre de" otro cliente.
 */
public record PedidoClienteRequest(
        @NotNull(message = "Indica la dirección de envío") Long idDireccionEnvio,
        @NotEmpty(message = "El pedido debe tener al menos un producto") @Valid List<LineaVentaRequest> lineas) {
}
