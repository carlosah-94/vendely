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
        List<VentaMoneda> ventasPagadas,
        BigDecimal ingresoPlanesMesUsd,      // lo que pagaron las tiendas por sus planes este mes
        long pagosPlanesMes,
        List<VentaMoneda> comisionesMes,     // comision de Vendely por las ventas del mes (por moneda)
        List<TiendasPlan> tiendasPorPlan
) {
    public record VentaMoneda(String moneda, BigDecimal monto) {}

    public record TiendasPlan(String plan, long tiendas) {}

    public static AdminResumenResponse desde(AdminRepository.Resumen r, List<AdminRepository.VentaMoneda> ventas,
                                             AdminRepository.IngresoPlanes planes,
                                             List<AdminRepository.VentaMoneda> comisiones,
                                             List<AdminRepository.TiendasPlan> porPlan) {
        return new AdminResumenResponse(r.getTiendasActivas(), r.getTiendasSuspendidas(), r.getDuenos(),
                r.getClientes(), r.getPedidos(),
                ventas.stream().map(v -> new VentaMoneda(v.getMoneda(), v.getMonto())).toList(),
                planes.getMonto(), planes.getPagos(),
                comisiones.stream().map(v -> new VentaMoneda(v.getMoneda(), v.getMonto())).toList(),
                porPlan.stream().map(p -> new TiendasPlan(p.getPlan(), p.getTiendas())).toList());
    }
}
