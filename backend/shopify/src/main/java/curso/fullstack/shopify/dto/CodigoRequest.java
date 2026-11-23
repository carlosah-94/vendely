package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** El código de 6 dígitos de la app de autenticación. */
public record CodigoRequest(
        @NotBlank(message = "Escribe el código de 6 dígitos")
        @Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos")
        String codigo) {
}
