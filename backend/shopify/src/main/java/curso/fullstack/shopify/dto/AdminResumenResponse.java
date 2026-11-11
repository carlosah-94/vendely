package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.AdminRepository;
import java.math.BigDecimal;
import java.util.List;

/**
 * Números generales de Vendely para el superadmin.
 * ventasPagadas va por moneda (soles, pesos chilenos...): cada tienda cobra en la moneda de su país
 * y sumarlas sin convertir daría un número falso.
 */
public record AdminResumenResponse(
        long tiendasActivas,
        long tiendasSuspendidas,
        long duenos,
        long clientes,
        long pedidos,
        List<VentaMoneda> ventasPagadas
) {
    public record VentaMoneda(String moneda, BigDecimal monto) {}

    public static AdminResumenResponse desde(AdminRepository.Resumen r, List<AdminRepository.VentaMoneda> ventas) {
        return new AdminResumenResponse(r.getTiendasActivas(), r.getTiendasSuspendidas(), r.getDuenos(),
                r.getClientes(), r.getPedidos(),
                ventas.stream().map(v -> new VentaMoneda(v.getMoneda(), v.getMonto())).toList());
    }
}
