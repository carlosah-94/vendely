package curso.fullstack.shopify.dto;

import java.math.BigDecimal;
import java.util.List;

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
        String mensaje,
        // Formas de entrega para esa dirección (V18): courier, delivery de la tienda y recojo.
        // El cliente elige una; vacío = no hay forma de entregarle ahí.
        List<OpcionEntrega> opciones
) {
    /** tipo: ENVIO, DELIVERY o RECOJO. costo ya con el envío gratis aplicado. detalle: plazo o dirección del local. */
    public record OpcionEntrega(String tipo, String nombre, BigDecimal costo, String detalle) {}
}
