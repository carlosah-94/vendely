package curso.fullstack.shopify.dto;

import java.time.OffsetDateTime;

/**
 * Cómo va la conversación de una tienda con soporte, para la lista del equipo de Vendely.
 *
 * sinResponder: cuántos mensajes del dueño llegaron después de la última respuesta de soporte
 * (0 = está al día). ultimoTexto / ultimaFecha / ultimoDeSoporte: el último mensaje, o null si
 * todavía no escribió nadie.
 */
public record BandejaSoporteResponse(Long idTienda, int sinResponder, String ultimoTexto,
                                     OffsetDateTime ultimaFecha, Boolean ultimoDeSoporte) {
}
