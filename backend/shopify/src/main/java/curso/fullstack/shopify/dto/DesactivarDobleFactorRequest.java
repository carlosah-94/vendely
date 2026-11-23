package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Para quitar la doble autenticación se pide la contraseña y un código: si alguien deja la
 * sesión abierta, quien se siente en esa computadora no puede desactivarla.
 */
public record DesactivarDobleFactorRequest(
        @NotBlank(message = "Escribe tu contraseña")
        String contrasena,

        @NotBlank(message = "Escribe el código de 6 dígitos")
        @Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos")
        String codigo) {
}
