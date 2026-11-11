package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cambio de inventario de una variante. Se envía al menos uno:
 * - recibir: unidades que llegaron (se SUMAN al stock y se anota la fecha de reposición).
 * - cantidad: corregir el stock a un valor exacto (por ejemplo, después de contar el almacén).
 * - stockMinimo: debajo de este número, el producto se marca con "stock bajo".
 */
public record InventarioRequest(
        @Min(value = 1, message = "Recibe al menos 1 unidad") @Max(value = 100000, message = "Máximo 100000 unidades por vez")
        Integer recibir,

        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer cantidad,

        @Min(value = 0, message = "El stock mínimo no puede ser negativo")
        Integer stockMinimo
) {}
