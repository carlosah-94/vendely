package curso.fullstack.shopify.dto;

import java.math.BigDecimal;

/**
 * Cuánto cuesta enviar a una dirección del cliente, antes de pagar.
 * disponible = false: la tienda no envía ahí (mensaje explica por qué) y el pedido no se puede crear.
 * internacional = true: venta al extranjero, sin el impuesto de la tienda (IGV, IVA) (los impuestos de importación los paga el
 * comprador al recibir).
 */
public record CotizacionEnvioResponse(
        boolean disponible,
        boolean internacional,
        String idPais,
        String pais,
        BigDecimal costo,              // ya aplicado el envío gratis, si corresponde
        BigDecimal envioGratisDesde,
        Short diasEntregaMin,
        Short diasEntregaMax,
        String mensaje
) {}
