package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** El payment_id que Mercado Pago agrega a la URL de regreso. Solo sirve para CONSULTAR el pago. */
public record ConfirmarPagoRequest(
        @NotBlank(message = "Indica el id del pago")
        @Pattern(regexp = "^[0-9]{1,20}$", message = "El id del pago no es válido")
        String paymentId) {
}
