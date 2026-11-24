package curso.fullstack.shopify.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Informe de ventas de una tienda entre dos fechas (panel → Informes).
 * Solo cuenta los pedidos con el pago APROBADO (un pedido cancelado después de cobrarlo queda
 * REEMBOLSADO y sale del informe). Los montos van en la moneda de la tienda.
 *
 * anterior: el mismo número de días justo antes de "desde", para comparar.
 */
public record InformeResponse(
        LocalDate desde,
        LocalDate hasta,
        Resumen actual,
        Resumen anterior,
        List<Dia> porDia,
        List<ProductoVendido> productos,
        List<MetodoPago> metodosPago,
        Clientes clientes,
        List<EstadoPedidos> estados
) {
    /**
     * ventas = suma de los totales (productos - código + envío); ticketPromedio = ventas / pedidos;
     * descuentos = lo rebajado por ofertas automáticas y por códigos de descuento.
     */
    public record Resumen(BigDecimal ventas, long pedidos, BigDecimal ticketPromedio, long unidades,
                          BigDecimal descuentos, BigDecimal envios, long clientes) {}

    /** Un día del periodo (también los días sin ventas, con 0). */
    public record Dia(LocalDate fecha, BigDecimal ventas, long pedidos) {}

    /** ingresos = lo cobrado por ese producto (precio menos la oferta, por la cantidad). */
    public record ProductoVendido(Long idProducto, String nombre, long unidades, BigDecimal ingresos) {}

    /** metodo: PAYPAL, MERCADO_PAGO u OTRO. */
    public record MetodoPago(String metodo, long pedidos, BigDecimal monto) {}

    /** nuevos: su primera compra pagada en la tienda fue en el periodo; recurrentes: ya habían comprado antes. */
    public record Clientes(long nuevos, long recurrentes) {}

    /** estado: SIN_ENVIAR, ENVIADO, ENTREGADO o CANCELADO. */
    public record EstadoPedidos(String estado, long pedidos) {}
}
