package curso.fullstack.shopify.tiemporeal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Avisos en vivo de la conversación con soporte: así ni el dueño ni el equipo de Vendely tienen
 * que recargar la página para ver la respuesta del otro.
 *
 *  - Al canal de la tienda: "hay un mensaje nuevo" (la pantalla vuelve a pedir la conversación).
 *  - Al canal del equipo: "QoriPet escribió a soporte" (la campanita y la lista de Soporte a tiendas).
 *
 * Como TiempoRealListener, nunca lanza: el mensaje ya quedó guardado.
 */
@Slf4j
@Component
public class TiempoRealSoporte {

    /**
     * tipo: MENSAJE (alguien escribió) · ACCESO (el dueño dio o quitó el acceso).
     * deSoporte: quién escribió. texto: un adelanto corto del mensaje, para el aviso.
     */
    public record Aviso(String tipo, Long idTienda, String tienda, boolean deSoporte, String texto) {
    }

    private static final int ADELANTO = 120;

    private final SimpMessagingTemplate mensajes;

    public TiempoRealSoporte(SimpMessagingTemplate mensajes) {
        this.mensajes = mensajes;
    }

    /**
     * Un mensaje nuevo, a la tienda y al equipo. Al equipo también le llegan las respuestas de
     * soporte: así el contador de "sin responder" baja solo en las pantallas de todos.
     */
    public void mensaje(Long idTienda, String tienda, boolean deSoporte, String texto) {
        Aviso aviso = new Aviso("MENSAJE", idTienda, tienda, deSoporte, adelanto(texto));
        enviar(Canales.soporte(idTienda), aviso);
        enviar(Canales.SOPORTE_EQUIPO, aviso);
    }

    /** El dueño dio o quitó el acceso: la lista del equipo cambia. */
    public void acceso(Long idTienda, String tienda) {
        enviar(Canales.SOPORTE_EQUIPO, new Aviso("ACCESO", idTienda, tienda, false, null));
    }

    private static String adelanto(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() > ADELANTO ? texto.substring(0, ADELANTO) + "…" : texto;
    }

    private void enviar(String canal, Aviso aviso) {
        try {
            mensajes.convertAndSend(canal, aviso);
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar el aviso de soporte a {}: {}", canal, ex.getMessage());
        }
    }
}
