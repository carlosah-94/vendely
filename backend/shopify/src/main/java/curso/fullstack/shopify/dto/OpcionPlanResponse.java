package curso.fullstack.shopify.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lo que pasaria si el dueno elige este plan hoy.
 * tipo: SUBIR (empieza hoy y descuenta los dias no usados), BAJAR (empieza cuando termina
 * lo ya pagado) o RENOVAR (los 30 dias se suman al final).
 */
public record OpcionPlanResponse(
        Short idPlan,
        String tipo,
        BigDecimal precioUsd,       // precio de lista del plan
        BigDecimal creditoUsd,      // descuento por los dias no usados (solo al SUBIR)
        BigDecimal montoUsd,        // lo que se cobrara en PayPal
        OffsetDateTime empiezaEl,
        // Solo al BAJAR: lo que la tienda dejará de tener desde empiezaEl. Nada se borra: todo
        // vuelve a verse si después mejora el plan. Vacío si no pierde nada.
        List<String> dejaraDeTener
) {}
