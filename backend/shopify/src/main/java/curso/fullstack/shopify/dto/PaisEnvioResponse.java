package curso.fullstack.shopify.dto;

/** Un país al que la tienda vende. internacional = false es el país de la propia tienda. */
public record PaisEnvioResponse(String idPais, String nombre, boolean internacional) {}
