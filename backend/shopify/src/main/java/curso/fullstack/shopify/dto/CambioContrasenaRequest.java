package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CambioContrasenaRequest(
        @NotBlank String contrasenaActual,

        @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe tener al menos una letra y un número")
        String contrasenaNueva
) {}
