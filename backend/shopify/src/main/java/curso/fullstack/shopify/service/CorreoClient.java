package curso.fullstack.shopify.service;

import org.springframework.stereotype.Component;

/**
 * La única puerta de salida de los correos de Vendely: el código de verificación del registro y
 * los avisos de los pedidos pasan por aquí.
 *
 * Elige el servicio así: Brevo si tiene su clave y su remitente (llega a cualquier persona);
 * si no, Resend si tiene su clave; si no hay ninguno, estaConfigurado() es false y quien llama
 * deja el correo como "simulado" (en local, el código de verificación se escribe en el log).
 *
 * Es como el mostrador de envíos de una oficina: quien manda la carta no elige la empresa de
 * mensajería, solo la deja aquí.
 */
@Component
public class CorreoClient {

    private final BrevoClient brevo;
    private final ResendClient resend;

    public CorreoClient(BrevoClient brevo, ResendClient resend) {
        this.brevo = brevo;
        this.resend = resend;
    }

    /** true si hay algún servicio de correo configurado (Brevo o Resend). */
    public boolean estaConfigurado() {
        return brevo.estaConfigurado() || resend.estaConfigurado();
    }

    /** "Brevo", "Resend" o "ninguno": solo para los logs. */
    public String servicio() {
        return brevo.estaConfigurado() ? "Brevo" : resend.estaConfigurado() ? "Resend" : "ninguno";
    }

    /**
     * Manda el correo con el servicio que corresponda y devuelve su id.
     * Los errores (RestClientException) los maneja quien llama.
     */
    public String enviar(String destinatario, String asunto, String html, String nombreAdjunto, byte[] adjunto) {
        if (brevo.estaConfigurado()) {
            return brevo.enviar(destinatario, asunto, html, nombreAdjunto, adjunto);
        }
        return resend.enviar(destinatario, asunto, html, nombreAdjunto, adjunto);
    }
}
