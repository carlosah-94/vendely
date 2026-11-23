package curso.fullstack.shopify.service;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envía correos con la API de Resend (sin SDK, igual que MercadoPagoClient y PayPalClient).
 *
 * Sin RESEND_API_KEY no se envía nada: NotificacionService lo pregunta antes y registra el
 * correo como "simulado". Así el proyecto corre completo en cualquier máquina, sin cuentas
 * ni claves, y el flujo del pedido no depende de un servicio externo.
 *
 * Los errores (RestClientException) los maneja NotificacionService: un correo que no sale
 * queda registrado como fallido y el pedido sigue su curso.
 */
@Component
public class ResendClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    private final RestClient http;
    private final String remitente;
    private final boolean configurado;

    public ResendClient(@Value("${app.resend.api-key:}") String apiKey,
                        @Value("${app.resend.remitente:Vendely <onboarding@resend.dev>}") String remitente) {
        String clave = apiKey == null ? "" : apiKey.trim();
        this.configurado = !clave.isEmpty();
        this.remitente = remitente == null || remitente.isBlank()
                ? "Vendely <onboarding@resend.dev>" : remitente.trim();
        this.http = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + clave)
                .build();
    }

    /** false si falta RESEND_API_KEY: entonces los correos se registran sin enviarse. */
    public boolean estaConfigurado() {
        return configurado;
    }

    /**
     * Manda un correo y devuelve el id que asigna Resend.
     * El adjunto es opcional (el comprobante en PDF); se envía en base64, como pide la API.
     */
    public String enviar(String destinatario, String asunto, String html, String nombreAdjunto, byte[] adjunto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("from", remitente);
        cuerpo.put("to", List.of(destinatario));
        cuerpo.put("subject", asunto);
        cuerpo.put("html", html);
        if (adjunto != null && adjunto.length > 0 && nombreAdjunto != null) {
            cuerpo.put("attachments", List.of(Map.of(
                    "filename", nombreAdjunto,
                    "content", Base64.getEncoder().encodeToString(adjunto))));
        }
        Map<String, Object> r = http.post().uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JSON);
        return r == null ? null : String.valueOf(r.get("id"));
    }
}
