package curso.fullstack.shopify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Pedido de un invitado (sin cuenta). La dirección se crea antes y se manda con su clave: así nadie
 * puede usar la dirección de otro invitado. El correo es a donde llegan la confirmación y el
 * comprobante, con el enlace secreto para ver el pedido.
 */
public record PedidoInvitadoRequest(
        @NotBlank(message = "Escribe tu correo") @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo puede tener hasta 150 caracteres") String correo,
        @NotNull(message = "Indica la dirección de envío") Long idDireccionEnvio,
        @NotBlank(message = "Falta la clave de la dirección") String claveDireccion,
        @NotEmpty(message = "El pedido debe tener al menos un producto") @Valid List<LineaVentaRequest> lineas,
        @Size(max = 40, message = "El código puede tener hasta 40 caracteres") String codigoCupon,
        @Pattern(regexp = "^(ENVIO|DELIVERY|RECOJO)$", message = "La forma de entrega debe ser ENVIO, DELIVERY o RECOJO")
        String tipoEntrega,
        @Valid DatosComprobante comprobante) {
}
