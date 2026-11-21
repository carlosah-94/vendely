package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotNull;

/** La suscripcion que vuelve de PayPal (viene en la URL de regreso: ?suscripcion=12). */
public record ConfirmarPlanRequest(@NotNull(message = "Falta la suscripcion") Long idSuscripcion) {}
