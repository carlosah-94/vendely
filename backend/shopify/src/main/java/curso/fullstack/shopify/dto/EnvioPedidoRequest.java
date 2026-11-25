package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Size;

/**
 * Datos con los que el dueño marca un pedido como enviado (issue #3).
 * Por courier los dos son obligatorios: sin ellos el cliente no puede rastrear su paquete, y la
 * base tampoco acepta un pedido enviado sin empresa ni número (ck_pedidos_envio). Lo revisa
 * PedidoService, porque en delivery propio y en recojo en tienda (V18) no hay número que rastrear.
 */
public record EnvioPedidoRequest(
        @Size(max = 60, message = "La empresa de envío no puede pasar de 60 caracteres")
        String empresaEnvio,

        @Size(max = 60, message = "El número de seguimiento no puede pasar de 60 caracteres")
        String numeroSeguimiento) {
}
