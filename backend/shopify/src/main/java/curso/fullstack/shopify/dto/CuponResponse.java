package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Cupon;
import curso.fullstack.shopify.entity.Oferta;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Un código de descuento del panel.
 * estado: ACTIVO, PROGRAMADO (empieza después), VENCIDO, AGOTADO (llegó al máximo de usos) o PAUSADO.
 * usos: pedidos pagados con el código.
 */
public record CuponResponse(Long idCupon, String codigo, String tipoDescuento, BigDecimal valor,
                            BigDecimal compraMinima, Integer usosMaximos, boolean unaVezPorCliente,
                            OffsetDateTime fechaInicio, OffsetDateTime fechaFin, boolean activo, String estado,
                            long usos) {

    public static CuponResponse desde(Cupon c, long usos, OffsetDateTime ahora) {
        return new CuponResponse(c.getIdCupon(), c.getCodigo(),
                c.getTipoDescuento() == Oferta.PORCENTAJE ? "PORCENTAJE" : "MONTO_FIJO", c.getValor(),
                c.getCompraMinima(), c.getUsosMaximos(), c.isUnaVezPorCliente(), c.getFechaInicio(), c.getFechaFin(),
                c.getEstado() == Cupon.ACTIVO, estado(c, usos, ahora), usos);
    }

    static String estado(Cupon c, long usos, OffsetDateTime ahora) {
        if (c.getEstado() != Cupon.ACTIVO) {
            return "PAUSADO";
        }
        if (c.getFechaFin() != null && !c.getFechaFin().isAfter(ahora)) {
            return "VENCIDO";
        }
        if (c.getUsosMaximos() != null && usos >= c.getUsosMaximos()) {
            return "AGOTADO";
        }
        return c.getFechaInicio().isAfter(ahora) ? "PROGRAMADO" : "ACTIVO";
    }
}
