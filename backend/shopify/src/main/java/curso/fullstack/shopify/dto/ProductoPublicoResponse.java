package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.ProductoRepository;
import java.math.BigDecimal;

/**
 * Producto para el catálogo público (issue #10), lo que ve un visitante sin iniciar sesión.
 * Es un DTO aparte del panel porque no muestra datos internos (estado). El stock se envía para
 * que la tienda limite la cantidad del carrito; el backend vuelve a validarlo al crear el pedido.
 */
public record ProductoPublicoResponse(
        Long idProducto,
        String nombre,
        String slug,
        String descripcion,
        String marca,
        Long idVariante,
        BigDecimal precio,
        Long stock,
        boolean disponible,   // true si hay stock
        String imagenUrl      // primera imagen, o null si no tiene
) {
    public static ProductoPublicoResponse desde(ProductoRepository.Fila f) {
        long stock = f.getStock() == null ? 0 : f.getStock();
        return new ProductoPublicoResponse(f.getIdProducto(), f.getNombre(), f.getSlug(), f.getDescripcion(),
                f.getMarca(), f.getIdVariante(), f.getPrecio(), stock, stock > 0, f.getImagenUrl());
    }
}
