package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.ProductoRepository;
import java.math.BigDecimal;

/**
 * Producto para el catálogo público (issue #10), lo que ve un visitante sin iniciar sesión.
 * Es un DTO aparte del panel porque no muestra datos internos (estado, cantidad exacta de stock):
 * solo si está disponible.
 */
public record ProductoPublicoResponse(
        Long idProducto,
        String nombre,
        String slug,
        String descripcion,
        String marca,
        Long idVariante,
        BigDecimal precio,
        boolean disponible   // true si hay stock
) {
    public static ProductoPublicoResponse desde(ProductoRepository.Fila f) {
        return new ProductoPublicoResponse(f.getIdProducto(), f.getNombre(), f.getSlug(), f.getDescripcion(),
                f.getMarca(), f.getIdVariante(), f.getPrecio(), f.getStock() != null && f.getStock() > 0);
    }
}
