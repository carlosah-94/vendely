package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos con los que el dueño marca un pedido como enviado (issue #3).
 * Los dos son obligatorios: sin ellos el cliente no puede rastrear su paquete, y la base
 * tampoco acepta un pedido enviado sin empresa ni número (ck_pedidos_envio).
 */
public record EnvioPedidoRequest(
        @NotBlank(message = "La empresa de envío es obligatoria")
        @Size(max = 60, message = "La empresa de envío no puede pasar de 60 caracteres")
        String empresaEnvio,

        @NotBlank(message = "El número de seguimiento es obligatorio")
        @Size(max = 60, message = "El número de seguimiento no puede pasar de 60 caracteres")
        String numeroSeguimiento) {
}
