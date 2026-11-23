package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.service.PagoWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Donde las pasarelas avisan que un pago cambió de estado. Es la pieza que hace que un pedido
 * pagado quede aprobado aunque el cliente nunca vuelva a la pantalla.
 *
 * Estas dos rutas son las únicas de /api/public/** que aceptan POST sin token (ver SecurityConfig):
 * quien llama es Mercado Pago o PayPal, no un navegador con sesión. Lo que las protege es la firma
 * del propio aviso, que PagoWebhookService comprueba antes de tocar nada.
 *
 * Qué se responde y por qué: las pasarelas reintentan el aviso cuando reciben un 5xx, así que
 *   · 200 = recibido (aunque el aviso no corresponda a ningún pedido de Vendely: reintentarlo
 *           no cambiaría nada y la pasarela lo seguiría mandando durante días);
 *   · 401 = la firma no cuadra, no se tocó ningún pago;
 *   · 5xx = el problema es nuestro (la base o la pasarela no respondieron): vuelve a intentarlo.
 */
@Slf4j
@RestController
@RequestMapping("/api/public/pagos/webhook")
public class PagoWebhookController {

    private final PagoWebhookService webhooks;

    public PagoWebhookController(PagoWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    /** Aviso de Mercado Pago (IPN). El id del pago viene en ?data.id= y también en el cuerpo. */
    @PostMapping("/mercadopago")
    public ResponseEntity<Void> mercadoPago(
            @RequestHeader(value = "x-signature", required = false) String firma,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestParam(value = "type", required = false) String tipo,
            @RequestBody(required = false) String cuerpo) {
        try {
            webhooks.mercadoPago(firma, requestId, dataId, tipo, cuerpo);
        } catch (ApiException e) {
            return recibido(e, "Mercado Pago");
        }
        return ResponseEntity.ok().build();
    }

    /** Aviso de PayPal. Las seis cabeceras paypal-* son las que PayPal usa para firmarlo. */
    @PostMapping("/paypal")
    public ResponseEntity<Void> payPal(
            @RequestHeader(value = "paypal-auth-algo", required = false) String authAlgo,
            @RequestHeader(value = "paypal-cert-url", required = false) String certUrl,
            @RequestHeader(value = "paypal-transmission-id", required = false) String transmissionId,
            @RequestHeader(value = "paypal-transmission-sig", required = false) String transmissionSig,
            @RequestHeader(value = "paypal-transmission-time", required = false) String transmissionTime,
            @RequestBody(required = false) String cuerpo) {
        try {
            webhooks.payPal(cuerpo, authAlgo, certUrl, transmissionId, transmissionSig, transmissionTime);
        } catch (ApiException e) {
            return recibido(e, "PayPal");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * El 401 y los errores nuestros (5xx) se devuelven tal cual, para que la pasarela reintente.
     * Lo demás (el aviso no es de un pedido, el monto no coincide, el pago ya se usó) se acepta
     * con 200: ya quedó registrado en el log y reintentarlo daría el mismo resultado.
     */
    private ResponseEntity<Void> recibido(ApiException e, String pasarela) {
        if (e.getStatus() == HttpStatus.UNAUTHORIZED || e.getStatus().is5xxServerError()) {
            throw e;
        }
        log.info("Aviso de {} recibido y no aplicado: {}", pasarela, e.getMessage());
        return ResponseEntity.ok().build();
    }
}
