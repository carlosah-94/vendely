package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Crear o editar un producto simple (issue #10, validaciones RF2.1.1).
 * Se usa el mismo DTO para POST y PUT porque los campos y sus reglas son iguales:
 * al editar se envía el producto completo.
 */
public record ProductoRequest(
        // RF2.1.1: 3 a 200 caracteres (la columna es VARCHAR(200)). Que sea único en la tienda lo revisa el service.
        @NotBlank @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
        String nombre,

        // RF2.1.1: máximo 500. En la base es TEXT (sin límite): el límite es una regla del negocio.
        @Size(max = 500, message = "La descripción puede tener hasta 500 caracteres")
        String descripcion,

        @Size(max = 100) String marca,

        // RF2.1.1: precio mayor que 0 (la base permite 0, pero vender gratis no tiene sentido aquí).
        // @Digits: como mucho 2 decimales (céntimos), igual que NUMERIC(12,2).
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor que 0")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales")
        BigDecimal precio,

        // RF2.1.1: stock mayor o igual a 0. Es el valor absoluto: 25 deja 25 unidades.
        @NotNull(message = "El stock es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stock,

        // Opcional. null o true = activo (visible en el catálogo y vendible); false = borrador (oculto).
        Boolean activo
) {}
