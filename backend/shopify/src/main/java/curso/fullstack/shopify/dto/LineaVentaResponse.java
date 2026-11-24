// dto/LineaVentaResponse.java
package curso.fullstack.shopify.dto;

import java.math.BigDecimal;

public record LineaVentaResponse(Long idVariante, String nombreProducto, String nombreVariante,
        Integer cantidad, BigDecimal precioUnitario, BigDecimal importe,
        // Oferta automática: lo que se rebajó por unidad (0 sin oferta). importe = (precio - descuento) × cantidad.
        BigDecimal descuentoUnitario, String nombreOferta) {
}