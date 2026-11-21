package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotNull;

/** El plan que el dueno quiere pagar (2 Basico · 3 Pro). */
public record PagarPlanRequest(@NotNull(message = "Elige un plan") Short idPlan) {}
