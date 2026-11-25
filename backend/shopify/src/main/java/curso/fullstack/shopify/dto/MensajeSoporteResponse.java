package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.MensajeSoporte;
import java.time.OffsetDateTime;

/** Un mensaje de la conversación. deSoporte: true lo escribió soporte de Vendely, false el dueño. */
public record MensajeSoporteResponse(Long idMensaje, boolean deSoporte, String texto, OffsetDateTime fecha) {

    public static MensajeSoporteResponse desde(MensajeSoporte m) {
        return new MensajeSoporteResponse(m.getIdMensaje(), Boolean.TRUE.equals(m.getDeSoporte()), m.getTexto(), m.getFecha());
    }
}
