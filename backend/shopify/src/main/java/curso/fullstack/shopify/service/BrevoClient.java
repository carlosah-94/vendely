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
 * Envía correos con la API de Brevo (sin SDK, igual que ResendClient).
 *
 * Por qué Brevo: con el remitente de pruebas de Resend solo se puede escribir al correo del dueño
 * de la cuenta de Resend. Brevo deja verificar un correo personal como remitente (sin dominio
 * propio) y escribir a cualquier persona, con 300 correos al día gratis. Así el código de
 * verificación del registro le llega a quien se está registrando.
 *
 * Variables: BREVO_API_KEY (clave de la API), BREVO_REMITENTE_CORREO (el correo verificado en
 * Brevo como remitente) y BREVO_REMITENTE_NOMBRE (el nombre que ve quien recibe, "Vendely").
 * Si falta la clave o el correo remitente, no se usa y CorreoClient prueba con Resend.
 */
@Component
public class BrevoClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    private final RestClient http;
    private final String correoRemitente;
    private final String nombreRemitente;
    private final boolean configurado;

    public BrevoClient(@Value("${app.brevo.api-key:}") String apiKey,
                       @Value("${app.brevo.remitente-correo:}") String correoRemitente,
                       @Value("${app.brevo.remitente-nombre:Vendely}") String nombreRemitente) {
        String clave = apiKey == null ? "" : apiKey.trim();
        this.correoRemitente = correoRemitente == null ? "" : correoRemitente.trim();
        this.nombreRemitente = nombreRemitente == null || nombreRemitente.isBlank() ? "Vendely" : nombreRemitente.trim();
        this.configurado = !clave.isEmpty() && !this.correoRemitente.isEmpty();
        this.http = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", clave)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** false si falta BREVO_API_KEY o BREVO_REMITENTE_CORREO. */
    public boolean estaConfigurado() {
        return configurado;
    }

    /**
     * Manda un correo y devuelve el id que asigna Brevo (messageId).
     * El adjunto es opcional (el comprobante en PDF); va en base64, como pide la API.
     */
    public String enviar(String destinatario, String asunto, String html, String nombreAdjunto, byte[] adjunto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("sender", Map.of("name", nombreRemitente, "email", correoRemitente));
        cuerpo.put("to", List.of(Map.of("email", destinatario)));
        cuerpo.put("subject", asunto);
        cuerpo.put("htmlContent", html);
        if (adjunto != null && adjunto.length > 0 && nombreAdjunto != null) {
            cuerpo.put("attachment", List.of(Map.of(
                    "name", nombreAdjunto,
                    "content", Base64.getEncoder().encodeToString(adjunto))));
        }
        Map<String, Object> r = http.post().uri("/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JSON);
        return r == null ? null : String.valueOf(r.get("messageId"));
    }
}
