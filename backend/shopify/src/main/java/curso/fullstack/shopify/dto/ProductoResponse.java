package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.ProductoRepository;
import java.math.BigDecimal;

/**
 * Producto para el panel del dueño (issue #10): datos del producto + precio vigente + stock.
 * idVariante se incluye porque es lo que se envía al registrar una venta (issue #7).
 */
public record ProductoResponse(
        Long idProducto,
        String nombre,
        String slug,
        String descripcion,
        String marca,
        String estado,      // "ACTIVO", "BORRADOR" o "ARCHIVADO"
        Long idVariante,
        BigDecimal precio,
        Long stock
) {
    public static ProductoResponse desde(ProductoRepository.Fila f) {
        return new ProductoResponse(f.getIdProducto(), f.getNombre(), f.getSlug(), f.getDescripcion(),
                f.getMarca(), nombreEstado(f.getEstado()), f.getIdVariante(), f.getPrecio(), f.getStock());
    }

    // La base guarda el estado como número (CHECK 0..2); al frontend se le envía un texto legible.
    static String nombreEstado(Short estado) {
        return switch (estado) {
            case 0 -> "BORRADOR";
            case 1 -> "ACTIVO";
            case 2 -> "ARCHIVADO";
            default -> "DESCONOCIDO";
        };
    }
}
