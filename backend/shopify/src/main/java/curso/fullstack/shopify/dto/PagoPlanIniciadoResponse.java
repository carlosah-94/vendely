package curso.fullstack.shopify.dto;

/** A donde enviar al dueno para que pague en PayPal. */
public record PagoPlanIniciadoResponse(Long idSuscripcion, String urlPago) {}
