package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Crear o editar un código de descuento desde el panel. */
public record CuponRequest(
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^\s*[A-Za-z0-9_-]{3,40}\s*$",
                 message = "De 3 a 40 letras, números, guiones o guiones bajos, sin espacios")
        String codigo,

        @NotNull(message = "Elige el tipo de descuento")
        @Pattern(regexp = "PORCENTAJE|MONTO_FIJO", message = "Tipo de descuento no válido")
        String tipoDescuento,

        @NotNull(message = "El valor es obligatorio")
        @Positive(message = "El valor debe ser mayor que 0")
        BigDecimal valor,

        // null = sin compra mínima
        @Positive(message = "La compra mínima debe ser mayor que 0")
        BigDecimal compraMinima,

        // null = sin límite
        @Positive(message = "El máximo de usos debe ser mayor que 0")
        Integer usosMaximos,

        Boolean unaVezPorCliente,

        // null = empieza ahora
        OffsetDateTime fechaInicio,

        // null = sin fecha de fin
        OffsetDateTime fechaFin,

        // null o true = activo; false = pausado
        Boolean activo
) {}
