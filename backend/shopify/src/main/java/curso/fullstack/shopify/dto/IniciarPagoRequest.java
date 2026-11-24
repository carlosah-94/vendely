package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.service.MetodoPago;

/**
 * Cuerpo de POST /api/cliente/pedidos/{id}/pago: con qué pasarela quiere pagar el cliente.
 *
 * El cuerpo es opcional: sin él (o con metodo null) se usa el primer método disponible de la
 * tienda, que es lo que hacía el backend antes de que existiera la elección. Un valor que no es
 * del enum ("BITCOIN") lo rechaza Jackson y GlobalExceptionHandler responde 400.
 */
public record IniciarPagoRequest(MetodoPago metodo) {
}
