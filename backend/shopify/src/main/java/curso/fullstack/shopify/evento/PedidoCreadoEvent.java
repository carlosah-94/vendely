package curso.fullstack.shopify.evento;

/** Se publica cuando se guarda un pedido nuevo (panel o tienda publica). */
public record PedidoCreadoEvent(Long idTienda, Long idPedido) {
}
