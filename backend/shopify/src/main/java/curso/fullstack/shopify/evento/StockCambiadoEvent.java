package curso.fullstack.shopify.evento;

/** Se publica cuando cambia el stock de una variante. cantidad = stock que queda. */
public record StockCambiadoEvent(Long idTienda, Long idVariante, Integer cantidad) {
}
