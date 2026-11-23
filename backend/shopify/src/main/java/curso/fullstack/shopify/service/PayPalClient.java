package curso.fullstack.shopify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Llama a la API REST de PayPal (Orders v2, sin SDK). Por defecto usa el entorno Sandbox (pruebas).
 * El cliente paga en la página de PayPal, así que los datos de su tarjeta o cuenta nunca pasan por aquí.
 * Los errores (RestClientException) los maneja PagoService.
 */
@Component
public class PayPalClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String webhookId;
    private final boolean configurado;

    // El permiso (access token) de PayPal dura varias horas: se guarda y se reutiliza hasta que venza.
    private String accessToken;
    private Instant venceEn = Instant.EPOCH;

    public PayPalClient(ObjectMapper objectMapper,
                        @Value("${app.paypal.client-id:}") String clientId,
                        @Value("${app.paypal.client-secret:}") String clientSecret,
                        @Value("${app.paypal.webhook-id:}") String webhookId,
                        @Value("${app.paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.webhookId = webhookId == null ? "" : webhookId.trim();
        this.configurado = !this.clientId.isEmpty() && !this.clientSecret.isEmpty();
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** false si falta PAYPAL_WEBHOOK_ID: sin él no se puede comprobar que un aviso sea de PayPal. */
    public boolean tieneWebhookId() {
        return !webhookId.isEmpty();
    }

    /**
     * Le pregunta a PayPal si el aviso es auténtico. PayPal firma cada aviso con su certificado y
     * esta llamada compara esa firma contra el cuerpo que llegó.
     *
     * Por eso el cuerpo se reenvía TAL CUAL vino (cuerpoCrudo), sin pasarlo por un objeto y volver
     * a escribirlo: un espacio distinto o otro orden de las claves daría una firma que no coincide.
     */
    public boolean firmaValidaDelAviso(String cuerpoCrudo, String authAlgo, String certUrl,
                                      String transmissionId, String transmissionSig, String transmissionTime) {
        String cuerpo = "{"
                + "\"auth_algo\":" + comoJson(authAlgo) + ","
                + "\"cert_url\":" + comoJson(certUrl) + ","
                + "\"transmission_id\":" + comoJson(transmissionId) + ","
                + "\"transmission_sig\":" + comoJson(transmissionSig) + ","
                + "\"transmission_time\":" + comoJson(transmissionTime) + ","
                + "\"webhook_id\":" + comoJson(webhookId) + ","
                + "\"webhook_event\":" + cuerpoCrudo
                + "}";
        Map<String, Object> r = http.post().uri("/v1/notifications/verify-webhook-signature")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JSON);
        return r != null && "SUCCESS".equals(String.valueOf(r.get("verification_status")));
    }

    /** El valor como texto JSON, con sus comillas y escapes (los datos vienen de cabeceras). */
    private String comoJson(String valor) {
        try {
            return objectMapper.writeValueAsString(valor == null ? "" : valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo escribir el valor como JSON", e);
        }
    }

    /** false si faltan PAYPAL_CLIENT_ID o PAYPAL_CLIENT_SECRET en el entorno. */
    public boolean estaConfigurado() {
        return configurado;
    }

    /** Crea una orden de pago. La respuesta trae el id de la orden y el enlace a donde enviar al cliente. */
    public Map<String, Object> crearOrden(Map<String, Object> cuerpo) {
        return http.post().uri("/v2/checkout/orders")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JSON);
    }

    /** Consulta una orden: estado, monto y a qué pedido pertenece. */
    public Map<String, Object> consultarOrden(String idOrden) {
        return http.get().uri("/v2/checkout/orders/{id}", idOrden)
                .header("Authorization", "Bearer " + token())
                .retrieve()
                .body(JSON);
    }

    /** Cobra una orden que el cliente ya aprobó en PayPal. Es el momento en que se mueve el dinero (de prueba). */
    public Map<String, Object> capturarOrden(String idOrden) {
        return http.post().uri("/v2/checkout/orders/{id}/capture", idOrden)
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JSON);
    }

    /** Pide (o reutiliza) el permiso de PayPal con el Client ID y el Secret. */
    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(venceEn)) {
            return accessToken;
        }
        Map<String, Object> r = http.post().uri("/v1/oauth2/token")
                .headers(h -> h.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(JSON);
        accessToken = r == null ? null : String.valueOf(r.get("access_token"));
        long segundos = r != null && r.get("expires_in") instanceof Number n ? n.longValue() : 300;
        venceEn = Instant.now().plusSeconds(Math.max(60, segundos - 60)); // un minuto de margen
        return accessToken;
    }
}
