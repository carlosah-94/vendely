package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Crear o editar una categoría de productos. */
public record CategoriaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre puede tener hasta 100 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción puede tener hasta 500 caracteres")
        String descripcion,

        // null o true = activa (se ve en la tienda); false = oculta.
        Boolean activa
) {}
