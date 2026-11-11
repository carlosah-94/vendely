package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Una combinación de valores con su propio precio, stock y SKU, por ejemplo "M / Negro".
 * idVariante: el de una variante que ya existe (al editar); null para una nueva.
 * valores: uno por cada opción, en el mismo orden que las opciones.
 * precio, precioComparacion y stock son opcionales: si no vienen, se usan los del producto.
 */
public record VarianteRequest(
        Long idVariante,

        @NotEmpty(message = "La variante necesita un valor por cada opción")
        List<@NotBlank(message = "Un valor no puede estar vacío") @Size(max = 50) String> valores,

        @DecimalMin(value = "0.01", message = "El precio debe ser mayor que 0")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales")
        BigDecimal precio,

        @DecimalMin(value = "0.01", message = "El precio de comparación debe ser mayor que 0")
        @Digits(integer = 10, fraction = 2, message = "El precio de comparación admite hasta 2 decimales")
        BigDecimal precioComparacion,

        @Size(max = 60, message = "El SKU puede tener hasta 60 caracteres")
        String sku,

        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stock
) {}
