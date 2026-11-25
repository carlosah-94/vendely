// dto/VentaResponse.java
package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Pedido;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record VentaResponse(Long idPedido, Integer numeroPedido, Long idLogin, String moneda,
        BigDecimal subtotal, BigDecimal costoEnvio, BigDecimal montoImpuestos,
        BigDecimal total, OffsetDateTime fechaCreacion, List<LineaVentaResponse> lineas,
        String estadoPago, String estado,
        // Seguimiento del envio (issue #3): con esto el cliente arma su linea de tiempo.
        String empresaEnvio, String numeroSeguimiento,
        OffsetDateTime fechaEnvio, OffsetDateTime fechaEntrega, OffsetDateTime fechaCancelacion,
        // Código de descuento (V14): total = subtotal - descuentoCupon + costoEnvio.
        BigDecimal descuentoCupon, String codigoCupon,
        // Cómo se entrega (V18): ENVIO, DELIVERY o RECOJO.
        String tipoEntrega,
        // Comprobante (V25): BOLETA o FACTURA (null fuera de Perú), su número B001-00000001 (null
        // hasta que se aprueba el pago) y el documento del cliente.
        String tipoComprobante, String numeroComprobante, String clienteDocTipo, String clienteDocNumero,
        String clienteRazonSocial) {

    public static VentaResponse desde(Pedido p, List<LineaVentaResponse> lineas, String estadoPago) {
        return desde(p, lineas, estadoPago, null);
    }

    public static VentaResponse desde(Pedido p, List<LineaVentaResponse> lineas, String estadoPago, String codigoCupon) {
        return new VentaResponse(p.getIdPedido(), p.getNumeroPedido(), p.getIdLogin(), p.getMoneda(),
                p.getSubtotal(), p.getCostoEnvio(), p.getMontoImpuestos(), p.getTotal(),
                p.getFechaCreacion(), lineas, estadoPago,
                PedidoResumenResponse.nombreEstado(p.getEstado()),
                p.getEmpresaEnvio(), p.getNumeroSeguimiento(),
                p.getFechaEnvio(), p.getFechaEntrega(), p.getFechaCancelacion(),
                p.getDescuentoCupon() == null ? BigDecimal.ZERO : p.getDescuentoCupon(), codigoCupon,
                curso.fullstack.shopify.entity.TipoEntrega.de(p.getTipoEntrega()).name(),
                p.getTipoComprobante() == null ? null : "F".equals(p.getTipoComprobante().trim()) ? "FACTURA" : "BOLETA",
                p.numeroComprobante(), p.getClienteDocTipo(), p.getClienteDocNumero(), p.getClienteRazonSocial());
    }
}
