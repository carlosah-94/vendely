package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Pedido;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Una fila de la lista de ventas (issue #8): número, fecha, cliente y total.
 * Es un DTO aparte de VentaResponse porque la lista no necesita las líneas: así la respuesta
 * es más liviana y no se consultan los productos de cada pedido.
 */
public record PedidoResumenResponse(Long idPedido, Integer numeroPedido, OffsetDateTime fechaCreacion,
                                    Long idLogin, String cliente, String moneda, BigDecimal total, String estado) {

    public static PedidoResumenResponse desde(Pedido p, String cliente) {
        return new PedidoResumenResponse(p.getIdPedido(), p.getNumeroPedido(), p.getFechaCreacion(),
                p.getIdLogin(), cliente, p.getMoneda(), p.getTotal(), nombreEstado(p.getEstado()));
    }

    // La base guarda el estado como número (CHECK 0..3); al frontend se le envía un texto legible.
    public static String nombreEstado(Short estado) {
        return switch (estado) {
            case 0 -> "SIN_ENVIAR";
            case 1 -> "ENVIADO";
            case 2 -> "ENTREGADO";
            case 3 -> "CANCELADO";
            default -> "DESCONOCIDO";
        };
    }
}