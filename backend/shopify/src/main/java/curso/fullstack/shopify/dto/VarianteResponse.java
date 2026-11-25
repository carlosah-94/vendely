package curso.fullstack.shopify.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una variante de un producto con su precio vigente y su stock.
 * nombre: los valores unidos ("M / Negro"), o "Única" en un producto simple.
 */
public record VarianteResponse(
        Long idVariante,
        List<String> valores,
        String nombre,
        BigDecimal precio,
        BigDecimal precioComparacion,
        BigDecimal costo,           // null en la tienda pública y para quien no ve las finanzas
        String sku,                 // null en la tienda pública
        long stock
) {}
