// dto/VentaResponse.java
package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Pedido;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record VentaResponse(Long idPedido, Integer numeroPedido, Long idLogin, String moneda,
        BigDecimal subtotal, BigDecimal costoEnvio, BigDecimal montoImpuestos,
        BigDecimal total, OffsetDateTime fechaCreacion, List<LineaVentaResponse> lineas,
        String estadoPago) {
    public static VentaResponse desde(Pedido p, List<LineaVentaResponse> lineas, String estadoPago) {
        return new VentaResponse(p.getIdPedido(), p.getNumeroPedido(), p.getIdLogin(), p.getMoneda(),
                p.getSubtotal(), p.getCostoEnvio(), p.getMontoImpuestos(), p.getTotal(),
                p.getFechaCreacion(), lineas, estadoPago);
    }
}
