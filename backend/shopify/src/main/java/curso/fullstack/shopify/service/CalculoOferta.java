package curso.fullstack.shopify.service;

import curso.fullstack.shopify.entity.Oferta;
import curso.fullstack.shopify.repository.OfertaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cuánto rebaja una oferta un precio. Es la MISMA fórmula que revisa el trigger T7 al guardar una
 * línea de pedido, así que el precio que ve el cliente en la tienda es el que se le cobra:
 * - porcentaje: round(precio × valor / 100) con los decimales de la moneda (HALF_UP, como round() de Postgres);
 * - monto fijo: el valor, sin pasar del precio.
 */
public final class CalculoOferta {

    private CalculoOferta() {
    }

    public static BigDecimal descuento(short tipoDescuento, BigDecimal valor, BigDecimal precio, int decimales) {
        if (tipoDescuento == Oferta.PORCENTAJE) {
            return precio.multiply(valor).divide(BigDecimal.valueOf(100), decimales, RoundingMode.HALF_UP);
        }
        return valor.min(precio);
    }

    public static BigDecimal descuento(OfertaRepository.Vigente o, BigDecimal precio, int decimales) {
        return descuento(o.getTipoDescuento(), o.getValor(), precio, decimales);
    }

    /** De las ofertas vigentes de un producto, la que más rebaja ese precio (vacío si no hay ninguna). */
    public static Optional<OfertaRepository.Vigente> mejor(List<OfertaRepository.Vigente> ofertas, BigDecimal precio,
                                                         int decimales) {
        if (ofertas == null || ofertas.isEmpty() || precio == null) {
            return Optional.empty();
        }
        return ofertas.stream()
                .max(Comparator.comparing((OfertaRepository.Vigente o) -> descuento(o, precio, decimales))
                        .thenComparing(OfertaRepository.Vigente::getIdOferta));
    }
}
