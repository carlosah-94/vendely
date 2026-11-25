package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Por cuántos días el dueño deja entrar a soporte de Vendely (de 1 a 7) y, opcional, qué
 * necesita que le armen. El mensaje inicia la conversación con soporte (V17).
 */
public record SoporteRequest(
        @NotNull(message = "Indica por cuántos días")
        @Min(value = 1, message = "El acceso dura al menos 1 día")
        @Max(value = 7, message = "El acceso dura como máximo 7 días")
        Integer dias,

        @Size(max = 2000, message = "El mensaje puede tener hasta 2000 caracteres")
        String mensaje
) {}
