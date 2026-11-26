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
        List<EstadoPedidos> estados,
        Inventario inventario
) {
    /**
     * ventas = suma de los totales (productos - código + envío); ticketPromedio = ventas / pedidos;
     * descuentos = lo rebajado por ofertas automáticas y por códigos de descuento.
     */
    public record Resumen(BigDecimal ventas, long pedidos, BigDecimal ticketPromedio, long unidades,
                          BigDecimal descuentos, BigDecimal envios, long clientes,
                          BigDecimal impuestos, Ganancia ganancia) {}

    /**
     * Cuánto le queda de verdad al dueño (V21). Solo cuenta los pedidos cuyos productos TODOS tienen costo
     * cargado (pedidosConCosto); los demás se avisan en pedidosSinCosto y no entran, para no inflar la ganancia.
     *
     * ingresosNetos = lo cobrado por los productos, sin el IGV (ese 18 % es del Estado) y sin el envío
     *                 (se paga al courier), y ya con las ofertas y los códigos de descuento.
     * costo         = lo que costaron esas unidades (cantidad × costo de ese día).
     * ganancia      = ingresosNetos - costo.   margen = ganancia / ingresosNetos, en porcentaje.
     */
    public record Ganancia(BigDecimal ingresosNetos, BigDecimal costo, BigDecimal ganancia, BigDecimal margen,
                           long pedidosConCosto, long pedidosSinCosto) {}

    /** Un día del periodo (también los días sin ventas, con 0). ganancia: de los pedidos con costo de ese día. */
    public record Dia(LocalDate fecha, BigDecimal ventas, long pedidos, BigDecimal ganancia) {}

    /**
     * ingresos = lo cobrado por ese producto (precio menos la oferta, por la cantidad, con IGV).
     * ganancia = su ganancia sin IGV en los pedidos con costo completo; null si no hay ninguno.
     */
    public record ProductoVendido(Long idProducto, String nombre, long unidades, BigDecimal ingresos,
                                  BigDecimal ganancia) {}

    /**
     * Lo que tiene el dueño en el almacén hoy (productos activos y borradores, no archivados).
     * capitalInvertido = stock × costo: el dinero que ya puso en mercadería.
     * gananciaPotencial = lo que quedaría (sin IGV) si vendiera todo al precio de hoy, menos ese capital.
     * Solo cuenta las variantes con costo; productosSinCosto avisa cuántos productos faltan por cargar.
     */
    public record Inventario(long unidades, BigDecimal capitalInvertido, BigDecimal valorDeVenta,
                             BigDecimal gananciaPotencial, long productosSinCosto) {}

    /** metodo: PAYPAL, MERCADO_PAGO u OTRO. */
    public record MetodoPago(String metodo, long pedidos, BigDecimal monto) {}

    /** nuevos: su primera compra pagada en la tienda fue en el periodo; recurrentes: ya habían comprado antes. */
    public record Clientes(long nuevos, long recurrentes) {}

    /** estado: SIN_ENVIAR, ENVIADO, ENTREGADO o CANCELADO. */
    public record EstadoPedidos(String estado, long pedidos) {}
}
