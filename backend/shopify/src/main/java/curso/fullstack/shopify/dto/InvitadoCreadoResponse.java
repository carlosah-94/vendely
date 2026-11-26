package curso.fullstack.shopify.dto;

/** Lo que recibe un invitado al crear su dirección o su pedido: el dato y su clave secreta. */
public record InvitadoCreadoResponse<T>(T datos, String clave) {
}
