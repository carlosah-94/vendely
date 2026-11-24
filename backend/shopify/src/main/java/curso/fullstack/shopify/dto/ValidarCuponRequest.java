package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * El checkout pregunta si un código sirve y cuánto rebaja. subtotal = el del carrito (productos,
 * ya con ofertas). Es solo una vista previa: al crear el pedido se vuelve a revisar con el subtotal real.
 */
public record ValidarCuponRequest(
        @NotBlank(message = "Escribe un código") String codigo,
        @NotNull(message = "Falta el subtotal") @PositiveOrZero BigDecimal subtotal) {
}
