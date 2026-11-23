package curso.fullstack.shopify.tiemporeal;

import curso.fullstack.shopify.security.UsuarioAutenticado;
import java.security.Principal;

/**
 * Quién está detrás de una conexión WebSocket.
 *
 * Spring identifica al usuario de una conexión por getName(), y lo usa para entregar los
 * mensajes de /user/queue/...: por eso el nombre es el idLogin. Así convertAndSendToUser("9003", …)
 * le llega solo a las conexiones abiertas por la cuenta 9003, en todas sus pestañas.
 */
public record UsuarioConectado(UsuarioAutenticado usuario) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(usuario.idLogin());
    }
}
