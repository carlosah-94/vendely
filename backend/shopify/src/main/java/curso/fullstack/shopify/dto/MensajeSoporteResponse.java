package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.MensajeSoporte;
import java.time.OffsetDateTime;

/**
 * Un mensaje de la conversación. deSoporte: true lo escribió soporte de Vendely, false el dueño.
 * adjunto: el archivo que trae (V26), o null.
 */
public record MensajeSoporteResponse(Long idMensaje, boolean deSoporte, String texto, OffsetDateTime fecha,
                                     Adjunto adjunto) {

    /** esImagen: el chat la muestra en miniatura; los demás archivos, como tarjeta para descargar. */
    public record Adjunto(String url, String nombre, String tipo, Long bytes, boolean esImagen) {
    }

    public static MensajeSoporteResponse desde(MensajeSoporte m) {
        Adjunto adjunto = !m.tieneAdjunto() ? null : new Adjunto(m.getAdjuntoUrl(), m.getAdjuntoNombre(),
                m.getAdjuntoTipo(), m.getAdjuntoBytes(),
                m.getAdjuntoTipo() != null && m.getAdjuntoTipo().startsWith("image/"));
        return new MensajeSoporteResponse(m.getIdMensaje(), Boolean.TRUE.equals(m.getDeSoporte()), m.getTexto(),
                m.getFecha(), adjunto);
    }
}
