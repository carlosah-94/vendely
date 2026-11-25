package curso.fullstack.shopify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Pedido que hace un cliente desde la tienda. No lleva idLogin ni idTienda:
 * salen del token, para que nadie compre "a nombre de" otro cliente.
 */
public record PedidoClienteRequest(
        @NotNull(message = "Indica la dirección de envío") Long idDireccionEnvio,
        @NotEmpty(message = "El pedido debe tener al menos un producto") @Valid List<LineaVentaRequest> lineas,
        // Código de descuento que escribió el cliente (null = ninguno). Se revisa al crear el pedido.
        @Size(max = 40, message = "El código puede tener hasta 40 caracteres") String codigoCupon,
        // Cómo lo recibe (V18): ENVIO (courier, por defecto), DELIVERY (la tienda lo lleva) o RECOJO.
        @Pattern(regexp = "^(ENVIO|DELIVERY|RECOJO)$", message = "La forma de entrega debe ser ENVIO, DELIVERY o RECOJO")
        String tipoEntrega,
        // Boleta o factura (V25). null = boleta sin documento.
        @Valid DatosComprobante comprobante) {
}
