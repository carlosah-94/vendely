package curso.fullstack.shopify.dto;

import java.util.List;

/**
 * Una página de resultados. Hasta ahora el catálogo devolvía todos los productos de una tienda
 * en un solo arreglo; con muchos productos eso es una respuesta enorme para mostrar doce.
 *
 * Se envía el total además del contenido porque la tienda necesita saber cuántos hay para
 * dibujar el paginador y para decir "28 productos" aunque solo haya recibido doce.
 *
 * pagina empieza en 1, como se lee en la URL (?pagina=2).
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long total, int paginas, boolean hayMas) {

    public static <T> PaginaResponse<T> de(List<T> contenido, int pagina, int tamano, long total) {
        int paginas = tamano <= 0 ? 0 : (int) Math.ceil((double) total / tamano);
        return new PaginaResponse<>(contenido, pagina, tamano, total, paginas, pagina < paginas);
    }
}
