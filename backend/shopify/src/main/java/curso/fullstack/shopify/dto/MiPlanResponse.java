package curso.fullstack.shopify.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Pantalla "Plan" del dueno: su plan, cuanto usa, cuando vence, los planes y que pasaria con cada uno. */
public record MiPlanResponse(
        PlanResponse plan,
        long productosUsados,
        OffsetDateTime venceEl,          // fin del periodo pagado actual (null en Gratis)
        List<PlanResponse> planes,
        List<OpcionPlanResponse> opciones
) {}
