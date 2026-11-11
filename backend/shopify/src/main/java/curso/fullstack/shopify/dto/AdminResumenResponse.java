package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.AdminRepository;
import java.math.BigDecimal;

/** Números generales de Vendely para el superadmin. */
public record AdminResumenResponse(
        long tiendasActivas,
        long tiendasSuspendidas,
        long duenos,
        long clientes,
        long pedidos,
        BigDecimal ventasPagadas
) {
    public static AdminResumenResponse desde(AdminRepository.Resumen r) {
        return new AdminResumenResponse(r.getTiendasActivas(), r.getTiendasSuspendidas(), r.getDuenos(),
                r.getClientes(), r.getPedidos(), r.getVentasPagadas());
    }
}
