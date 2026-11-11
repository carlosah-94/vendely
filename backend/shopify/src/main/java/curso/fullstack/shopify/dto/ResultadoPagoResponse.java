package curso.fullstack.shopify.dto;

/** Resultado de confirmar un pago: APROBADO, RECHAZADO o PENDIENTE. */
public record ResultadoPagoResponse(Long idPedido, Integer numeroPedido, String estadoPago) {}
