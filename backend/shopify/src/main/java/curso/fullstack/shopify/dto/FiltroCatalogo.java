package curso.fullstack.shopify.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Los filtros del catálogo público, tal como llegan en la URL (issue #4):
 * ?categoria=3&min=20&max=80&stock=1&q=polo&orden=precio_asc&pagina=2
 *
 * Todo viene de afuera, así que nada se usa tal cual: normalizado() recorta el texto, descarta
 * un orden inventado y limita el tamaño de la página. Sin eso, cualquiera podría pedir cien mil
 * productos en una sola petición.
 */
public record FiltroCatalogo(String q, Long categoria, BigDecimal min, BigDecimal max,
                             boolean stock, String orden, int pagina, int tamano) {

    public static final int TAMANO_POR_DEFECTO = 12;
    public static final int TAMANO_MAXIMO = 48;

    /** Los únicos ordenamientos válidos. Cualquier otro se trata como el de por defecto. */
    private static final List<String> ORDENES = List.of("nombre", "precio_asc", "precio_desc", "novedades");

    public FiltroCatalogo normalizado() {
        String texto = (q == null || q.isBlank()) ? null : q.trim();
        String ordenValido = (orden != null && ORDENES.contains(orden.trim().toLowerCase()))
                ? orden.trim().toLowerCase() : "nombre";
        // Un precio negativo o un mínimo mayor que el máximo no filtran nada útil: se ignoran.
        BigDecimal minimo = (min != null && min.signum() >= 0) ? min : null;
        BigDecimal maximo = (max != null && max.signum() >= 0) ? max : null;
        if (minimo != null && maximo != null && minimo.compareTo(maximo) > 0) {
            minimo = null;
            maximo = null;
        }
        int numero = Math.max(1, pagina);
        int cuantos = tamano <= 0 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
        return new FiltroCatalogo(texto, categoria, minimo, maximo, stock, ordenValido, numero, cuantos);
    }

    /** Cuántas filas saltarse para llegar a esta página (la 1 no se salta ninguna). */
    public int desplazamiento() {
        return (pagina - 1) * tamano;
    }
}
