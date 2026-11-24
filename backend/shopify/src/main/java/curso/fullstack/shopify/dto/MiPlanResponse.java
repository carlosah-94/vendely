package curso.fullstack.shopify.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Pantalla "Plan" del dueno: su plan, cuanto usa, cuando vence, los planes y que pasaria con cada uno. */
public record MiPlanResponse(
        PlanResponse plan,
        long productosUsados,
        OffsetDateTime venceEl,          // fin del periodo pagado actual (null en Gratis)
        List<PlanResponse> planes,
        List<OpcionPlanResponse> opciones,
        // Lo que la tienda deja de tener si no renueva y vuelve a Gratis al vencer (vacío en Gratis).
        List<String> alVencer
) {}
