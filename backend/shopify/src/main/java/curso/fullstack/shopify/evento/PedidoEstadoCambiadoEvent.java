package curso.fullstack.shopify.evento;

/** Se publica cuando un pedido cambia de estado (0 sin enviar, 1 enviado, 2 entregado, 3 cancelado). */
public record PedidoEstadoCambiadoEvent(Long idTienda, Long idPedido, Short estadoAnterior, Short estadoNuevo) {
}
