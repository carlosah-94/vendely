package curso.fullstack.shopify.dto;

import java.math.BigDecimal;

/** Un código válido para este carrito: cuánto rebaja. tipoDescuento: PORCENTAJE o MONTO_FIJO. */
public record CuponAplicadoResponse(String codigo, String tipoDescuento, BigDecimal valor, BigDecimal descuento) {
}
