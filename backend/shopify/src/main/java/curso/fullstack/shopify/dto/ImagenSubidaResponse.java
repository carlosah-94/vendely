package curso.fullstack.shopify.dto;

/**
 * Una foto recién subida: su enlace (el que se guarda y se muestra) y su identificador en
 * Cloudinary, que es con lo que se puede pedir que se borre.
 */
public record ImagenSubidaResponse(String url, String publicId) {
}
