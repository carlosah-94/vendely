package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Oferta;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Una oferta del panel.
 * estado: ACTIVA (vigente ahora), PROGRAMADA (empieza después), VENCIDA (ya terminó) o PAUSADA.
 * unidadesVendidas > 0 = ya se vendió con ella: no se cambian su tipo, valor ni inicio, y no se borra.
 */
public record OfertaResponse(Long idOferta, String nombre, String motivo, String tipoDescuento, BigDecimal valor,
                             OffsetDateTime fechaInicio, OffsetDateTime fechaFin, boolean activa, String estado,
                             List<Long> productos, long unidadesVendidas) {

    public static OfertaResponse desde(Oferta o, long unidadesVendidas, OffsetDateTime ahora) {
        return new OfertaResponse(o.getIdOferta(), o.getNombre(), nombreMotivo(o.getMotivo()),
                o.getTipoDescuento() == Oferta.PORCENTAJE ? "PORCENTAJE" : "MONTO_FIJO", o.getValor(),
                o.getFechaInicio(), o.getFechaFin(), o.getEstado() == Oferta.ACTIVA, estado(o, ahora),
                o.getProductos().stream().sorted().toList(), unidadesVendidas);
    }

    static String estado(Oferta o, OffsetDateTime ahora) {
        if (o.getEstado() != Oferta.ACTIVA) {
            return "PAUSADA";
        }
        if (o.getFechaFin() != null && !o.getFechaFin().isAfter(ahora)) {
            return "VENCIDA";
        }
        return o.getFechaInicio().isAfter(ahora) ? "PROGRAMADA" : "ACTIVA";
    }

    public static String nombreMotivo(Short motivo) {
        return switch (motivo) {
            case 1 -> "LIQUIDACION";
            case 2 -> "TEMPORADA";
            case 3 -> "LANZAMIENTO";
            default -> "OTRO";
        };
    }

    public static short codigoMotivo(String motivo) {
        return switch (motivo) {
            case "LIQUIDACION" -> 1;
            case "TEMPORADA" -> 2;
            case "LANZAMIENTO" -> 3;
            default -> 4;
        };
    }
}
