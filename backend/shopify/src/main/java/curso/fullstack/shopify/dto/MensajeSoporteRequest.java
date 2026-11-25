package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un mensaje para la conversación con soporte (del dueño o de soporte). */
public record MensajeSoporteRequest(
        @NotBlank(message = "Escribe tu mensaje")
        @Size(max = 2000, message = "El mensaje puede tener hasta 2000 caracteres")
        String mensaje
) {}
