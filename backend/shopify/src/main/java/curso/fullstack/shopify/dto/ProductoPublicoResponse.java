package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.List;

/**
 * Producto para el catálogo público (issue #10), lo que ve un visitante sin iniciar sesión.
 * Es un DTO aparte del panel porque no muestra datos internos (estado, SKU). El stock se envía para
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
        boolean disponible,            // true si hay stock
        String imagenUrl,              // primera imagen, o null si no tiene
        BigDecimal precioComparacion,  // precio "antes" (tachado), o null
        List<String> imagenes,         // todas las fotos en orden
        List<Long> categorias,         // ids de sus categorías (la tienda filtra por ellas)
        List<OpcionResponse> opciones, // vacía en un producto simple
        List<VarianteResponse> variantes   // cada combinación con su precio y stock (sin SKU)
) {
    public static ProductoPublicoResponse desde(ProductoRepository.Fila f, List<OpcionResponse> opciones,
                                                List<VarianteResponse> variantes) {
        long stock = f.getStock() == null ? 0 : f.getStock();
        return new ProductoPublicoResponse(f.getIdProducto(), f.getNombre(), f.getSlug(), f.getDescripcion(),
                f.getMarca(), f.getIdVariante(), f.getPrecio(), stock, stock > 0, f.getImagenUrl(),
                f.getPrecioComparacion(), ProductoResponse.separarImagenes(f.getImagenes()),
                ProductoResponse.separarIds(f.getCategorias()), opciones, variantes);
    }
}
