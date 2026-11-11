package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.InventarioRepository;
import java.time.Instant;

/**
 * Una variante en la página de Inventario.
 * estado: "AGOTADO" (0), "BAJO" (en el mínimo o debajo) u "OK".
 */
public record InventarioResponse(
        Long idVariante,
        Long idProducto,
        String producto,
        String variante,
        String sku,
        String imagenUrl,
        boolean productoActivo,
        int cantidad,
        int stockMinimo,
        String estado,
        String ultimaReposicion       // ISO 8601 en UTC, o null
) {
    public static InventarioResponse desde(InventarioRepository.Fila f) {
        int cantidad = f.getCantidad() == null ? 0 : f.getCantidad();
        int minimo = f.getStockMinimo() == null ? 0 : f.getStockMinimo();
        String estado = cantidad == 0 ? "AGOTADO" : (minimo > 0 && cantidad <= minimo ? "BAJO" : "OK");
        return new InventarioResponse(f.getIdVariante(), f.getIdProducto(), f.getProducto(), f.getVariante(),
                f.getSku(), f.getImagenUrl(), f.getEstadoProducto() != null && f.getEstadoProducto() == 1,
                cantidad, minimo, estado,
                f.getUltimaReposicion() == null ? null : Instant.ofEpochSecond(f.getUltimaReposicion()).toString());
    }
}
