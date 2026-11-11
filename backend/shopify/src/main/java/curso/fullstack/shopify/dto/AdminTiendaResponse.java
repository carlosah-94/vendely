package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.AdminRepository;
import java.math.BigDecimal;
import java.time.Instant;

/** Una tienda vista por el superadmin: datos, dueño y números. */
public record AdminTiendaResponse(
        Long idTienda,
        String nombre,
        String subdominio,
        String pais,
        String estado,              // "ACTIVA" o "SUSPENDIDA"
        String fechaCreacion,       // ISO 8601 en UTC, por ejemplo 2026-11-11T08:30:00Z
        String dueno,
        String correoDueno,
        long productos,
        long pedidos,
        BigDecimal ventasPagadas,
        String moneda               // la de la tienda: "PEN", "CLP"
) {
    public static AdminTiendaResponse desde(AdminRepository.FilaTienda f) {
        return new AdminTiendaResponse(f.getIdTienda(), f.getNombre(), f.getSubdominio(), f.getPais(),
                f.getEstado() != null && f.getEstado() == 1 ? "ACTIVA" : "SUSPENDIDA", Instant.ofEpochSecond(f.getFechaCreacion()).toString(),
                f.getDueno(), f.getCorreoDueno(), f.getProductos(), f.getPedidos(), f.getVentasPagadas(), f.getMoneda());
    }
}
