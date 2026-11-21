package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Plan;
import curso.fullstack.shopify.entity.Suscripcion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Un pago de plan: sirve para el historial y para el comprobante. */
public record SuscripcionResponse(
        Long idSuscripcion,
        String numeroComprobante,     // "S-000012"
        Short idPlan,
        String plan,                  // "Pro"
        BigDecimal montoUsd,          // lo que pago (con descuento si subio de plan)
        BigDecimal precioPlanUsd,     // precio de lista del plan
        String estado,                // PENDIENTE, PAGADA o CANCELADA
        OffsetDateTime fechaInicio,
        OffsetDateTime fechaFin,
        String referenciaPago,        // id de la orden de PayPal
        OffsetDateTime fechaCreacion
) {
    public static SuscripcionResponse desde(Suscripcion s, Plan plan) {
        String estado = switch (s.getEstado()) {
            case Suscripcion.PAGADA -> "PAGADA";
            case Suscripcion.CANCELADA -> "CANCELADA";
            default -> "PENDIENTE";
        };
        return new SuscripcionResponse(s.getIdSuscripcion(), String.format("S-%06d", s.getIdSuscripcion()),
                s.getIdPlan(), plan == null ? "" : plan.getNombre(), s.getMontoUsd(),
                plan == null ? null : plan.getPrecioUsd(), estado, s.getFechaInicio(), s.getFechaFin(),
                s.getReferenciaPago(), s.getFechaCreacion());
    }
}
