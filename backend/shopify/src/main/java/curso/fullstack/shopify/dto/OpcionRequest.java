package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Una opción del producto y sus valores, por ejemplo Talla: S, M, L (tabla producto_opciones). */
public record OpcionRequest(
        @NotBlank(message = "Escribe el nombre de la opción (por ejemplo Talla)")
        @Size(max = 50, message = "El nombre de la opción puede tener hasta 50 caracteres")
        String nombre,

        @NotEmpty(message = "Agrega al menos un valor (por ejemplo S, M, L)")
        @Size(max = 30, message = "Una opción puede tener hasta 30 valores")
        List<@NotBlank(message = "Un valor no puede estar vacío")
             @Size(max = 50, message = "Cada valor puede tener hasta 50 caracteres")
             String> valores
) {}
