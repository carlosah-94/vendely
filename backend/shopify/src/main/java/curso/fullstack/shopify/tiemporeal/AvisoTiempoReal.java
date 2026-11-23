package curso.fullstack.shopify.tiemporeal;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Lo que viaja por los canales. Es un aviso corto, no el pedido entero: la pantalla, al
 * recibirlo, vuelve a pedir sus datos por la API de siempre. Así lo que se muestra pasa por las
 * mismas reglas y permisos que cuando se recarga la página, y no hay que mantener dos formatos.
 *
 * Solo se envían los campos con valor (NON_NULL): un aviso de stock no lleva número de pedido.
 *
 * tipo: PEDIDO_CREADO · PAGO_APROBADO · ESTADO_CAMBIADO · STOCK
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AvisoTiempoReal(String tipo, Long idPedido, Integer numeroPedido, String estado,
                              BigDecimal total, String moneda, Long idVariante, Integer cantidad) {

    static AvisoTiempoReal pedido(String tipo, Long idPedido, Integer numero, String estado,
                                  BigDecimal total, String moneda) {
        return new AvisoTiempoReal(tipo, idPedido, numero, estado, total, moneda, null, null);
    }

    static AvisoTiempoReal stock(Long idVariante, Integer cantidad) {
        return new AvisoTiempoReal("STOCK", null, null, null, null, null, idVariante, cantidad);
    }
}
