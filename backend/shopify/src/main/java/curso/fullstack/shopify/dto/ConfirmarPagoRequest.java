package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * El código que la pasarela agrega a la URL de regreso: el payment_id de Mercado Pago o el token
 * (id de la orden) de PayPal. Solo sirve para CONSULTAR el pago en la pasarela, nunca para aprobarlo.
 */
public record ConfirmarPagoRequest(
        @NotBlank(message = "Indica el id del pago")
        @Pattern(regexp = "^[A-Za-z0-9-]{1,40}$", message = "El id del pago no es válido")
        String paymentId) {
}
