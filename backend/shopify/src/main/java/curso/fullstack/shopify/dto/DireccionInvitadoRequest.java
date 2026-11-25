package curso.fullstack.shopify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dirección de un invitado: como la de un cliente (país, distrito, dirección, referencia), más el
 * nombre de quien recibe y su teléfono, que en un cliente salen de su cuenta.
 */
public record DireccionInvitadoRequest(
        @NotBlank(message = "Escribe el nombre de quien recibe")
        @Size(max = 150, message = "El nombre puede tener hasta 150 caracteres")
        @Pattern(regexp = "^[^<>]*$", message = "No se permiten los signos < ni >") String destinatario,
        @NotBlank(message = "Escribe un teléfono de contacto")
        @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$",
                 message = "El teléfono debe tener formato internacional, por ejemplo +51987654321") String telefono,
        @NotNull @Valid DireccionRequest direccion) {
}
