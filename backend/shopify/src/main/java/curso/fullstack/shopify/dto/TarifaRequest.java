package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Crear o editar una tarifa de envío (a una región del país o a otro país).
 * Los montos van en la moneda de la tienda (soles en Perú, pesos sin decimales en Chile).
 */
public record TarifaRequest(
        @NotNull(message = "El costo es obligatorio (0 = envío gratis)")
        @DecimalMin(value = "0", message = "El costo no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El costo admite hasta 2 decimales")
        BigDecimal costo,

        // Opcional: desde este subtotal el envío es gratis.
        @DecimalMin(value = "0.01", message = "El monto para envío gratis debe ser mayor que 0")
        @Digits(integer = 10, fraction = 2, message = "El monto admite hasta 2 decimales")
        BigDecimal envioGratisDesde,

        @Min(value = 0, message = "Los días no pueden ser negativos") @Max(value = 365, message = "Máximo 365 días")
        Short diasEntregaMin,

        @Min(value = 0, message = "Los días no pueden ser negativos") @Max(value = 365, message = "Máximo 365 días")
        Short diasEntregaMax,

        // null o true = activa; false = pausada (no se vende a esa zona por ahora).
        Boolean activa
) {}
