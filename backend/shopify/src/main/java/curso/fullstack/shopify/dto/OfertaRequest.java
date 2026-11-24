package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Crear o editar una oferta (descuento automático) desde el panel. */
public record OfertaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre puede tener hasta 120 caracteres")
        String nombre,

        @NotNull(message = "Elige el motivo")
        @Pattern(regexp = "LIQUIDACION|TEMPORADA|LANZAMIENTO|OTRO", message = "Motivo no válido")
        String motivo,

        @NotNull(message = "Elige el tipo de descuento")
        @Pattern(regexp = "PORCENTAJE|MONTO_FIJO", message = "Tipo de descuento no válido")
        String tipoDescuento,

        @NotNull(message = "El valor es obligatorio")
        @Positive(message = "El valor debe ser mayor que 0")
        BigDecimal valor,

        // null = empieza ahora
        OffsetDateTime fechaInicio,

        // null = sin fecha de fin
        OffsetDateTime fechaFin,

        @NotEmpty(message = "Elige al menos un producto")
        List<Long> productos,

        // null o true = activa; false = pausada
        Boolean activa
) {}
