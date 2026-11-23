package curso.fullstack.shopify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import curso.fullstack.shopify.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Avisos de las pasarelas (webhook). Resuelven el problema de que, hoy, un pago solo se confirma
 * si el navegador del cliente vuelve: si cierra la pestaña o se le corta el internet, el pedido
 * quedaba PENDIENTE con el dinero ya cobrado.
 *
 * Esta clase hace una sola cosa: comprobar que el aviso es auténtico y sacar de él el identificador
 * del pago. Lo demás (consultar la pasarela, comparar el monto, aprobar el pago y publicar el
 * evento) lo hace PagoService, por el mismo camino que usa el regreso del navegador.
 *
 * Regla de oro, igual que en PagoService: el aviso NO es la fuente de la verdad. De él solo se
 * toma un identificador; el estado y el monto siempre se consultan en la pasarela.
 * En el log no se escribe nunca el cuerpo del aviso (puede traer datos del comprador).
 */
@Slf4j
@Service
public class PagoWebhookService {

    /** Los únicos avisos de PayPal que mueven un pago. El resto se acepta y se ignora. */
    private static final List<String> EVENTOS_PAYPAL = List.of(
            "CHECKOUT.ORDER.APPROVED", "PAYMENT.CAPTURE.COMPLETED", "PAYMENT.CAPTURE.DENIED");

    private final PagoService pagoService;
    private final PayPalClient payPal;
    private final ObjectMapper objectMapper;
    private final String secretoMercadoPago;

    public PagoWebhookService(PagoService pagoService, PayPalClient payPal, ObjectMapper objectMapper,
                              @Value("${app.mercadopago.webhook-secret:}") String secretoMercadoPago) {
        this.pagoService = pagoService;
        this.payPal = payPal;
        this.objectMapper = objectMapper;
        this.secretoMercadoPago = secretoMercadoPago == null ? "" : secretoMercadoPago.trim();
    }

    // ------------------------------------------------------------------ Mercado Pago (IPN)

    /**
     * Aviso de Mercado Pago. Llega como POST con el id en la URL (?data.id=123&type=payment) y
     * también en el cuerpo; se usa el de la URL, que es el que entra en la firma.
     *
     * @param firma      cabecera x-signature: "ts=...,v1=..."
     * @param requestId  cabecera x-request-id, parte de la firma
     * @param dataIdUrl  parámetro data.id de la URL
     * @param tipoUrl    parámetro type de la URL
     * @param cuerpo     cuerpo del aviso, tal como llegó
     */
    public void mercadoPago(String firma, String requestId, String dataIdUrl, String tipoUrl, String cuerpo) {
        if (secretoMercadoPago.isEmpty()) {
            // Sin MERCADOPAGO_WEBHOOK_SECRET no hay forma de saber si el aviso es auténtico, y un
            // aviso sin verificar no puede tocar un pago. 503 para que la pasarela lo reintente.
            log.warn("Llegó un aviso de Mercado Pago pero falta MERCADOPAGO_WEBHOOK_SECRET");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Los avisos de pago no están disponibles por ahora");
        }
        Map<String, Object> aviso = leer(cuerpo);
        String dataId = dataIdUrl != null ? dataIdUrl : texto(mapa(aviso.get("data")).get("id"));
        String tipo = tipoUrl != null ? tipoUrl : texto(aviso.get("type"));

        verificarFirmaMercadoPago(firma, requestId, dataId);

        if (tipo != null && !"payment".equals(tipo)) {
            // merchant_order, plan, subscription…: no son pagos de un pedido.
            log.info("Aviso de Mercado Pago de tipo {}: no aplica", tipo);
            return;
        }
        if (dataId == null) {
            log.info("Aviso de Mercado Pago sin data.id: no aplica");
            return;
        }
        pagoService.aplicarAvisoMercadoPago(dataId);
    }

    /**
     * Comprueba la firma del aviso. Mercado Pago manda en x-signature la hora (ts) y un HMAC-SHA256
     * (v1) de este texto, firmado con la clave del panel:
     *
     *   id:&lt;data.id&gt;;request-id:&lt;x-request-id&gt;;ts:&lt;ts&gt;;
     *
     * Las partes que no vengan se omiten. La comparación es byte a byte con MessageDigest.isEqual
     * (tiempo constante): comparar con equals filtraría, por el tiempo de respuesta, cuántos
     * caracteres del hash acertó quien lo esté intentando.
     */
    private void verificarFirmaMercadoPago(String firma, String requestId, String dataId) {
        if (firma == null || firma.isBlank()) {
            throw noAutenticado("sin cabecera x-signature");
        }
        String ts = null;
        String v1 = null;
        for (String parte : firma.split(",")) {
            String[] clave = parte.split("=", 2);
            if (clave.length != 2) {
                continue;
            }
            if ("ts".equals(clave[0].trim())) {
                ts = clave[1].trim();
            } else if ("v1".equals(clave[0].trim())) {
                v1 = clave[1].trim();
            }
        }
        if (ts == null || v1 == null) {
            throw noAutenticado("x-signature sin ts o sin v1");
        }

        StringBuilder manifiesto = new StringBuilder();
        if (dataId != null) {
            manifiesto.append("id:").append(dataId.toLowerCase()).append(";");
        }
        if (requestId != null && !requestId.isBlank()) {
            manifiesto.append("request-id:").append(requestId.trim()).append(";");
        }
        manifiesto.append("ts:").append(ts).append(";");

        byte[] esperada = hmacSha256(manifiesto.toString()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperada, v1.getBytes(StandardCharsets.UTF_8))) {
            throw noAutenticado("la firma no coincide");
        }
    }

    private String hmacSha256(String manifiesto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretoMercadoPago.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            log.error("No se pudo calcular la firma del aviso: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo verificar el aviso");
        }
    }

    // ------------------------------------------------------------------ PayPal (Webhooks)

    /**
     * Aviso de PayPal. La firma no se calcula aquí: se le pregunta a PayPal con el cuerpo tal como
     * llegó y las seis cabeceras paypal-* que lo acompañan.
     */
    public void payPal(String cuerpo, String authAlgo, String certUrl, String transmissionId,
                       String transmissionSig, String transmissionTime) {
        if (!payPal.estaConfigurado() || !payPal.tieneWebhookId()) {
            log.warn("Llegó un aviso de PayPal pero faltan sus credenciales o PAYPAL_WEBHOOK_ID");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Los avisos de pago no están disponibles por ahora");
        }
        if (cuerpo == null || cuerpo.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El aviso llegó sin cuerpo");
        }
        // El certificado lo verifica PayPal, pero la dirección la manda quien llama: si no es un
        // dominio de PayPal, el aviso se rechaza aquí sin pedirle nada a nadie.
        if (!certificadoDePayPal(certUrl)) {
            throw noAutenticado("cert_url que no es de PayPal");
        }

        boolean valida;
        try {
            valida = payPal.firmaValidaDelAviso(cuerpo, authAlgo, certUrl,
                    transmissionId, transmissionSig, transmissionTime);
        } catch (RestClientException e) {
            // No se pudo preguntar: 502 para que PayPal reintente (reintenta durante días).
            log.error("No se pudo verificar con PayPal la firma del aviso {}: {}", transmissionId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el aviso");
        }
        if (!valida) {
            throw noAutenticado("PayPal no reconoce la firma del aviso " + transmissionId);
        }

        Map<String, Object> aviso = leer(cuerpo);
        String evento = texto(aviso.get("event_type"));
        if (evento == null || !EVENTOS_PAYPAL.contains(evento)) {
            log.info("Aviso de PayPal de tipo {}: no aplica", evento);
            return;
        }
        Map<String, Object> recurso = mapa(aviso.get("resource"));

        // En CHECKOUT.ORDER.APPROVED el recurso ES la orden; en los avisos de cobro el recurso es
        // la captura y el id de la orden viene aparte. Si no viniera, queda el custom_id, que es
        // el número de pedido que Vendely puso al crear la orden.
        String idOrden = "CHECKOUT.ORDER.APPROVED".equals(evento)
                ? texto(recurso.get("id"))
                : texto(mapa(mapa(recurso.get("supplementary_data")).get("related_ids")).get("order_id"));
        Long idPedido = aLong(texto(recurso.get("custom_id")));

        if (idOrden == null && idPedido == null) {
            log.info("Aviso de PayPal {} sin id de orden ni custom_id: no aplica", evento);
            return;
        }
        pagoService.aplicarAvisoPayPal(idOrden, idPedido);
    }

    /** La dirección del certificado tiene que ser https y de un dominio de PayPal. */
    private static boolean certificadoDePayPal(String certUrl) {
        return certUrl != null && certUrl.matches("https://[A-Za-z0-9.-]+\\.paypal\\.com/.*");
    }

    // ------------------------------------------------------------------ apoyo

    private ApiException noAutenticado(String motivo) {
        log.warn("Aviso de pago rechazado: {}", motivo);
        return new ApiException(HttpStatus.UNAUTHORIZED, "Aviso no autenticado");
    }

    /** El cuerpo del aviso como mapa. Un cuerpo vacío o roto no es motivo para fallar con 500. */
    private Map<String, Object> leer(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> mapa = objectMapper.readValue(cuerpo, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return mapa == null ? Map.of() : mapa;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El aviso no es un JSON válido");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String texto(Object o) {
        return (o == null || o.toString().isBlank()) ? null : o.toString().trim();
    }

    private static Long aLong(String s) {
        try {
            return s == null ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
