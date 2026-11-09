// dto/LineaVentaResponse.java
package curso.fullstack.shopify.dto;

import java.math.BigDecimal;

public record LineaVentaResponse(Long idVariante, String nombreProducto, String nombreVariante,
        Integer cantidad, BigDecimal precioUnitario, BigDecimal importe) {
}