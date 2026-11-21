package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Lo que el superadmin puede cambiar de un plan. maxProductos null = ilimitados. */
public record PlanEditarRequest(
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        @Digits(integer = 6, fraction = 2, message = "Precio con hasta 2 decimales")
        BigDecimal precioUsd,

        @Min(value = 1, message = "Debe permitir al menos 1 producto")
        Integer maxProductos,

        @NotNull(message = "La comision es obligatoria")
        @DecimalMin(value = "0.00", message = "La comision va de 0 a 100")
        @DecimalMax(value = "100.00", message = "La comision va de 0 a 100")
        @Digits(integer = 3, fraction = 2, message = "Comision con hasta 2 decimales")
        BigDecimal comision,

        @NotNull Boolean permitePersonalizar,
        @NotNull Boolean permitePlantillas,
        @NotNull Boolean permiteEnvioInternacional,
        @NotNull Boolean permiteMasMetodosPago
) {}
