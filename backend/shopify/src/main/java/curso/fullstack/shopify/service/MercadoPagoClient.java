package curso.fullstack.shopify.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Llama a la API REST de Mercado Pago (sin SDK). Checkout Pro: el formulario de la tarjeta lo
 * muestra Mercado Pago en su propia página, así que los datos de la tarjeta nunca pasan por aquí.
 * Los errores (RestClientException) los maneja PagoService.
 */
@Component
public class MercadoPagoClient {

    private final RestClient http;
    private final boolean configurado;

    public MercadoPagoClient(@Value("${app.mercadopago.access-token:}") String token) {
        this.configurado = token != null && !token.isBlank();
        this.http = RestClient.builder()
                .baseUrl("https://api.mercadopago.com")
                .defaultHeader("Authorization", "Bearer " + (token == null ? "" : token.trim()))
                .build();
    }

    /** false si falta MERCADOPAGO_ACCESS_TOKEN en el entorno. */
    public boolean estaConfigurado() {
        return configurado;
    }

    /** Crea la "preferencia" de pago y devuelve su respuesta (incluye la URL a donde se envía al cliente). */
    public Map<String, Object> crearPreferencia(Map<String, Object> cuerpo) {
        return http.post().uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /** Consulta un pago en Mercado Pago (estado, monto, referencia del pedido). */
    public Map<String, Object> consultarPago(String idPago) {
        return http.get().uri("/v1/payments/{id}", idPago)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * Los pagos que Mercado Pago tiene registrados para un pedido (su external_reference),
     * del más reciente al más antiguo. Lo usa la conciliación: cuando el cliente no volvió a la
     * pantalla, el intento pendiente de Vendely no guardó ningún id y el pedido es lo único que
     * se puede preguntar.
     */
    public Map<String, Object> buscarPagosDelPedido(String referenciaExterna) {
        return http.get()
                .uri(b -> b.path("/v1/payments/search")
                        .queryParam("external_reference", referenciaExterna)
                        .queryParam("sort", "date_created")
                        .queryParam("criteria", "desc")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
