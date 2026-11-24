package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Plan;
import java.math.BigDecimal;

/** Un plan de Vendely con su precio, limites y beneficios. */
public record PlanResponse(
        Short idPlan,
        String nombre,
        BigDecimal precioUsd,
        Integer maxProductos,          // null = ilimitados
        BigDecimal comision,           // % por venta
        Boolean permitePersonalizar,
        Boolean permitePlantillas,
        Boolean permiteEnvioInternacional,
        Boolean permiteMasMetodosPago,
        Short maxEmpleados             // 0 = solo el dueño
) {
    public static PlanResponse desde(Plan p) {
        return new PlanResponse(p.getIdPlan(), p.getNombre(), p.getPrecioUsd(), p.getMaxProductos(), p.getComision(),
                p.getPermitePersonalizar(), p.getPermitePlantillas(), p.getPermiteEnvioInternacional(),
                p.getPermiteMasMetodosPago(), p.getMaxEmpleados());
    }
}
