package curso.fullstack.shopify.evento;

/** Se publica cuando la pasarela confirma el pago de un pedido. */
public record PagoAprobadoEvent(Long idTienda, Long idPedido) {
}
