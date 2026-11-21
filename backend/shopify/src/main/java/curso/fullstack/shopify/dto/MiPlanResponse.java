package curso.fullstack.shopify.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Pantalla "Plan" del dueno: su plan, cuanto usa, cuando vence y los planes para comparar. */
public record MiPlanResponse(
        PlanResponse plan,
        long productosUsados,
        OffsetDateTime venceEl,        // null en Gratis
        List<PlanResponse> planes
) {}
