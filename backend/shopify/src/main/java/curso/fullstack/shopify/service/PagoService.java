package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.PagoIniciadoResponse;
import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.dto.ResultadoPagoResponse;
import curso.fullstack.shopify.entity.Pago;
import curso.fullstack.shopify.entity.Pedido;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.PagoRepository;
import curso.fullstack.shopify.repository.PedidoRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Pago de un pedido con una pasarela en modo de prueba: PayPal (Sandbox) o Mercado Pago (Checkout Pro).
 * Se usa PayPal si están PAYPAL_CLIENT_ID y PAYPAL_CLIENT_SECRET; si no, Mercado Pago si está su token.
 *
 * Regla de oro: NUNCA se cree lo que manda el navegador. La URL de regreso trae
 * status=approved, pero cualquiera puede escribirla a mano: siempre se consulta el pago
 * en la pasarela (Mercado Pago o PayPal) y se comparan estado, pedido y monto.
 * En los logs no se escribe ningún dato de tarjeta (nunca llegan a este servidor).
 */
@Slf4j
@Service
public class PagoService {

    private final PedidoRepository pedidoRepository;
    private final PagoRepository pagoRepository;
    private final TiendaRepository tiendaRepository;
    private final MercadoPagoClient mercadoPago;
    private final PayPalClient payPal;
    private final BigDecimal tipoCambioPenUsd;
    private final BigDecimal tipoCambioClpUsd;
    private final String frontendUrl;

    public PagoService(PedidoRepository pedidoRepository, PagoRepository pagoRepository,
                       TiendaRepository tiendaRepository, MercadoPagoClient mercadoPago, PayPalClient payPal,
                       @Value("${app.paypal.tipo-cambio-pen-usd:3.75}") BigDecimal tipoCambioPenUsd,
                       @Value("${app.paypal.tipo-cambio-clp-usd:950}") BigDecimal tipoCambioClpUsd,
                       @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.pedidoRepository = pedidoRepository;
        this.pagoRepository = pagoRepository;
        this.tiendaRepository = tiendaRepository;
        this.mercadoPago = mercadoPago;
        this.payPal = payPal;
        this.tipoCambioPenUsd = tipoCambioPenUsd;
        this.tipoCambioClpUsd = tipoCambioClpUsd;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    // ------------------------------------------------------------------ iniciar el pago

    /**
     * Crea el intento de pago (pendiente) y la preferencia en Mercado Pago.
     * Si Mercado Pago falla se lanza ApiException: @Transactional deshace el registro de pago.
     */
    @Transactional
    public PagoIniciadoResponse iniciar(UsuarioAutenticado yo, Long idPedido) {
        Pedido p = pedidoDelCliente(yo, idPedido);
        if (pagoRepository.existsByIdPedidoAndEstado(idPedido, Pago.APROBADO)) {
            throw new ApiException(HttpStatus.CONFLICT, "El pedido #" + p.getNumeroPedido() + " ya está pagado");
        }
        Tienda tienda = tiendaRepository.findById(p.getIdTienda())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        if (payPal.estaConfigurado()) {
            return iniciarPayPal(p, tienda);
        }
        if (!mercadoPago.estaConfigurado()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El pago en línea no está disponible por ahora");
        }

        Pago pago = new Pago();
        pago.setIdPedido(idPedido);
        pago.setProveedor(Pago.MERCADO_PAGO);
        pago.setMonto(p.getTotal());      // el total que calculó la base: productos + envío
        pago.setEstado(Pago.PENDIENTE);
        pagoRepository.saveAndFlush(pago);

        Map<String, Object> preferencia = crearPreferencia(armarPreferencia(p, tienda), tienda);
        String url = texto(preferencia.get("sandbox_init_point"));   // pruebas; en producción: init_point
        if (url == null) {
            url = texto(preferencia.get("init_point"));
        }
        if (url == null) {
            log.error("Mercado Pago no devolvió URL de pago para el pedido #{}", p.getNumeroPedido());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago con Mercado Pago");
        }
        log.info("Pago iniciado: pedido #{} (id={}), monto {} {}", p.getNumeroPedido(), idPedido, p.getTotal(), p.getMoneda());
        return new PagoIniciadoResponse(url);
    }

    private Map<String, Object> armarPreferencia(Pedido p, Tienda tienda) {
        String retorno = frontendUrl + "/tienda/" + tienda.getSubdominio() + "/pago/resultado";

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", "Pedido #" + p.getNumeroPedido() + " - " + tienda.getNombre());
        item.put("quantity", 1);
        item.put("currency_id", p.getMoneda() == null ? "PEN" : p.getMoneda().trim());
        item.put("unit_price", p.getTotal());

        Map<String, Object> backUrls = new LinkedHashMap<>();
        backUrls.put("success", retorno);
        backUrls.put("failure", retorno);
        backUrls.put("pending", retorno);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("items", List.of(item));
        cuerpo.put("external_reference", String.valueOf(p.getIdPedido()));
        cuerpo.put("back_urls", backUrls);
        cuerpo.put("auto_return", "approved");
        return cuerpo;
    }

    /**
     * En local, Mercado Pago puede rechazar auto_return con una dirección http://localhost.
     * Si pasa, se reintenta sin auto_return: el cliente vuelve igual con "Volver al sitio".
     */
    private Map<String, Object> crearPreferencia(Map<String, Object> cuerpo, Tienda tienda) {
        try {
            return mercadoPago.crearPreferencia(cuerpo);
        } catch (RestClientResponseException e) {
            String respuesta = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 400 && respuesta.contains("auto_return")) {
                log.info("Mercado Pago rechazó auto_return con la URL local; se reintenta sin él");
                cuerpo.remove("auto_return");
                try {
                    return mercadoPago.crearPreferencia(cuerpo);
                } catch (RestClientException e2) {
                    throw falloMercadoPago(e2);
                }
            }
            throw falloMercadoPago(e);
        } catch (RestClientException e) {
            throw falloMercadoPago(e);
        }
    }

    private ApiException falloMercadoPago(RestClientException e) {
        if (e instanceof RestClientResponseException r) {
            log.error("Mercado Pago respondió {} al crear la preferencia: {}", r.getStatusCode().value(),
                    recortar(r.getResponseBodyAsString()));
        } else {
            log.error("No se pudo conectar con Mercado Pago: {}", e.getMessage());
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago con Mercado Pago");
    }

    // ------------------------------------------------------------------ confirmar el pago

    /**
     * Consulta el pago EN Mercado Pago y actualiza la tabla pagos. Aprueba solo si el estado es
     * approved, la referencia externa es ESTE pedido y el monto es exactamente el total.
     */
    @Transactional
    public ResultadoPagoResponse confirmar(UsuarioAutenticado yo, Long idPedido, String paymentId) {
        Pedido p = pedidoDelCliente(yo, idPedido);

        // Ya aprobado antes (por ejemplo, el cliente recargó la página): no se cobra ni se cambia nada.
        if (pagoRepository.existsByIdPedidoAndEstado(idPedido, Pago.APROBADO)) {
            return resultado(p, Pago.APROBADO);
        }

        // Este pago de Mercado Pago ya se registró antes: si es de este pedido se devuelve su estado;
        // si es de otro, no se puede usar para este (la referencia es única).
        Optional<Pago> existente = pagoRepository.findByReferenciaTransaccion(paymentId);
        if (existente.isPresent()) {
            if (!existente.get().getIdPedido().equals(idPedido)) {
                log.warn("Intento de usar el pago {} del pedido {} para el pedido {}",
                        paymentId, existente.get().getIdPedido(), idPedido);
                throw new ApiException(HttpStatus.CONFLICT, "Ese pago ya se usó en otro pedido");
            }
            // Orden de PayPal todavía pendiente: hay que verificarla y cobrarla.
            if (existente.get().getProveedor() == Pago.PAYPAL && existente.get().getEstado() == Pago.PENDIENTE) {
                return confirmarPayPal(p, existente.get());
            }
            return resultado(p, existente.get().getEstado());
        }
        if (!paymentId.matches("^[0-9]{1,20}$")) {
            // No es un id de Mercado Pago ni una orden de PayPal creada para este pedido.
            throw new ApiException(HttpStatus.NOT_FOUND, "No se encontró ese pago");
        }

        Map<String, Object> mp = consultarPago(paymentId);

        // El pago debe ser de ESTE pedido y por EL monto del pedido (nunca confiar en el navegador).
        String referencia = texto(mp.get("external_reference"));
        BigDecimal monto = numero(mp.get("transaction_amount"));
        if (!String.valueOf(idPedido).equals(referencia)
                || monto == null || p.getTotal() == null || monto.compareTo(p.getTotal()) != 0) {
            log.warn("Pago {} no corresponde al pedido {}: referencia={}, monto={}, total del pedido={}",
                    paymentId, idPedido, referencia, monto, p.getTotal());
            throw new ApiException(HttpStatus.BAD_REQUEST, "El pago no corresponde a este pedido");
        }

        String estadoMp = texto(mp.get("status"));
        Pago pago = pagoRepository.findFirstByIdPedidoAndEstadoOrderByIdPagoDesc(idPedido, Pago.PENDIENTE)
                .orElseGet(() -> {
                    Pago nuevo = new Pago();
                    nuevo.setIdPedido(idPedido);
                    nuevo.setProveedor(Pago.MERCADO_PAGO);
                    return nuevo;
                });
        pago.setMonto(p.getTotal());

        if ("approved".equals(estadoMp)) {
            pago.setEstado(Pago.APROBADO);
            pago.setFechaPago(OffsetDateTime.now());
            pago.setReferenciaTransaccion(paymentId);
            guardar(pago);
            log.info("Pago aprobado: pedido #{}, monto {}", p.getNumeroPedido(), p.getTotal());
            return resultado(p, Pago.APROBADO);
        }
        if ("rejected".equals(estadoMp) || "cancelled".equals(estadoMp)) {
            pago.setEstado(Pago.RECHAZADO);
            pago.setReferenciaTransaccion(paymentId);
            guardar(pago);
            log.info("Pago rechazado: pedido #{}, estado en Mercado Pago {}", p.getNumeroPedido(), estadoMp);
            return resultado(p, Pago.RECHAZADO);
        }
        // pending, in_process, authorized…: todavía no hay resultado; se deja pendiente.
        log.info("Pago pendiente: pedido #{}, estado en Mercado Pago {}", p.getNumeroPedido(), estadoMp);
        return resultado(p, Pago.PENDIENTE);
    }

    private void guardar(Pago pago) {
        try {
            pagoRepository.saveAndFlush(pago);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // uq_pago_aprobado o referencia única: otra petición registró este pago a la vez.
            throw new ApiException(HttpStatus.CONFLICT, "Este pago ya fue registrado");
        }
    }

    private Map<String, Object> consultarPago(String paymentId) {
        if (!mercadoPago.estaConfigurado()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El pago en línea no está disponible por ahora");
        }
        try {
            Map<String, Object> mp = mercadoPago.consultarPago(paymentId);
            if (mp == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
            }
            return mp;
        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            log.warn("Mercado Pago respondió {} al consultar el pago {}", codigo, paymentId);
            if (codigo == 404 || codigo == 400) {
                throw new ApiException(HttpStatus.NOT_FOUND, "No se encontró ese pago en Mercado Pago");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
        } catch (RestClientException e) {
            log.error("No se pudo conectar con Mercado Pago: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
        }
    }

    // ------------------------------------------------------------------ PayPal (Sandbox)

    /**
     * Crea la orden en PayPal y guarda el intento (pendiente) con el id de la orden como referencia.
     * Así, al volver, solo se acepta una orden que se creó para ESTE pedido.
     * PayPal no cobra en soles ni en pesos chilenos: el monto se convierte a USD (ver montoPayPal).
     */
    private PagoIniciadoResponse iniciarPayPal(Pedido p, Tienda tienda) {
        BigDecimal montoUsd = montoPayPal(p);
        String retorno = frontendUrl + "/tienda/" + tienda.getSubdominio() + "/pago/resultado?pedido=" + p.getIdPedido();

        Map<String, Object> monto = new LinkedHashMap<>();
        monto.put("currency_code", "USD");
        monto.put("value", montoUsd.toPlainString());

        Map<String, Object> unidad = new LinkedHashMap<>();
        unidad.put("reference_id", String.valueOf(p.getIdPedido()));
        unidad.put("custom_id", String.valueOf(p.getIdPedido()));
        unidad.put("description", recortar("Pedido #" + p.getNumeroPedido() + " - " + tienda.getNombre(), 120));
        unidad.put("amount", monto);

        Map<String, Object> experiencia = new LinkedHashMap<>();
        experiencia.put("brand_name", recortar(tienda.getNombre(), 120));
        experiencia.put("shipping_preference", "NO_SHIPPING");   // la dirección ya la tiene Vendely
        experiencia.put("user_action", "PAY_NOW");
        experiencia.put("return_url", retorno);
        experiencia.put("cancel_url", retorno + "&cancelado=1");

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("intent", "CAPTURE");
        cuerpo.put("purchase_units", List.of(unidad));
        cuerpo.put("payment_source", Map.of("paypal", Map.of("experience_context", experiencia)));

        Map<String, Object> orden;
        try {
            orden = payPal.crearOrden(cuerpo);
        } catch (RestClientException e) {
            throw falloPayPal("crear la orden", e);
        }
        String idOrden = orden == null ? null : texto(orden.get("id"));
        String url = enlace(orden, "payer-action");
        if (url == null) {
            url = enlace(orden, "approve");
        }
        if (idOrden == null || url == null) {
            log.error("PayPal no devolvió la orden o el enlace de pago para el pedido #{}", p.getNumeroPedido());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago con PayPal");
        }

        Pago pago = new Pago();
        pago.setIdPedido(p.getIdPedido());
        pago.setProveedor(Pago.PAYPAL);
        pago.setMonto(p.getTotal());              // en la moneda del pedido, como indica la columna
        pago.setEstado(Pago.PENDIENTE);
        pago.setReferenciaTransaccion(idOrden);
        guardar(pago);

        log.info("Pago iniciado con PayPal: pedido #{} (id={}), {} {} = {} USD",
                p.getNumeroPedido(), p.getIdPedido(), p.getTotal(), p.getMoneda(), montoUsd);
        return new PagoIniciadoResponse(url);
    }

    /**
     * Verifica la orden EN PayPal (que sea de este pedido y por el monto correcto) y la cobra.
     * Solo se aprueba si PayPal confirma el cobro como COMPLETED.
     */
    private ResultadoPagoResponse confirmarPayPal(Pedido p, Pago pago) {
        String idOrden = pago.getReferenciaTransaccion();
        Map<String, Object> orden = consultarOrdenPayPal(idOrden);

        Map<String, Object> unidad = primeraUnidad(orden);
        Map<String, Object> monto = mapa(unidad.get("amount"));
        String pedidoDeLaOrden = texto(unidad.get("custom_id")) != null
                ? texto(unidad.get("custom_id")) : texto(unidad.get("reference_id"));
        BigDecimal valor = numero(monto.get("value"));
        if (!String.valueOf(p.getIdPedido()).equals(pedidoDeLaOrden)
                || !"USD".equals(texto(monto.get("currency_code")))
                || valor == null || valor.compareTo(montoPayPal(p)) != 0) {
            log.warn("Orden de PayPal {} no corresponde al pedido {}: pedido={}, monto={}",
                    idOrden, p.getIdPedido(), pedidoDeLaOrden, valor);
            throw new ApiException(HttpStatus.BAD_REQUEST, "El pago no corresponde a este pedido");
        }

        String estadoOrden = texto(orden.get("status"));
        if ("COMPLETED".equals(estadoOrden)) {
            return aprobarPayPal(p, pago);              // ya se había cobrado (por ejemplo, recargó la página)
        }
        if (!"APPROVED".equals(estadoOrden)) {
            // CREATED o PAYER_ACTION_REQUIRED: el cliente no aprobó el pago en PayPal.
            return rechazarPayPal(p, pago, "estado de la orden " + estadoOrden);
        }

        Map<String, Object> captura;
        try {
            captura = payPal.capturarOrden(idOrden);
        } catch (RestClientResponseException e) {
            if (e.getResponseBodyAsString().contains("ORDER_ALREADY_CAPTURED")) {
                return aprobarPayPal(p, pago);
            }
            if (e.getStatusCode().value() == 422) {    // por ejemplo INSTRUMENT_DECLINED: el medio de pago fue rechazado
                return rechazarPayPal(p, pago, "PayPal rechazó el cobro");
            }
            throw falloPayPal("cobrar la orden", e);
        } catch (RestClientException e) {
            throw falloPayPal("cobrar la orden", e);
        }

        String estadoCobro = texto(primeraCaptura(captura).get("status"));
        if ("COMPLETED".equals(estadoCobro)) {
            return aprobarPayPal(p, pago);
        }
        if ("DECLINED".equals(estadoCobro) || "FAILED".equals(estadoCobro)) {
            return rechazarPayPal(p, pago, "estado del cobro " + estadoCobro);
        }
        log.info("Pago pendiente en PayPal: pedido #{}, estado del cobro {}", p.getNumeroPedido(), estadoCobro);
        return resultado(p, Pago.PENDIENTE);
    }

    private ResultadoPagoResponse aprobarPayPal(Pedido p, Pago pago) {
        pago.setEstado(Pago.APROBADO);
        pago.setFechaPago(OffsetDateTime.now());
        guardar(pago);
        log.info("Pago aprobado con PayPal: pedido #{}, monto {} {}", p.getNumeroPedido(), p.getTotal(), p.getMoneda());
        return resultado(p, Pago.APROBADO);
    }

    private ResultadoPagoResponse rechazarPayPal(Pedido p, Pago pago, String motivo) {
        pago.setEstado(Pago.RECHAZADO);
        guardar(pago);
        log.info("Pago no completado con PayPal: pedido #{} ({})", p.getNumeroPedido(), motivo);
        return resultado(p, Pago.RECHAZADO);
    }

    /**
     * Monto a cobrar en PayPal (USD): el total si el pedido ya está en USD; si está en soles (PEN)
     * o pesos chilenos (CLP), convertido con un tipo de cambio fijo de la configuración.
     * PayPal no acepta esas monedas; el pedido se guarda siempre en la moneda de la tienda.
     */
    private BigDecimal montoPayPal(Pedido p) {
        String moneda = p.getMoneda() == null ? "PEN" : p.getMoneda().trim();
        if ("USD".equals(moneda)) {
            return p.getTotal().setScale(2, RoundingMode.HALF_UP);
        }
        if ("PEN".equals(moneda)) {
            return p.getTotal().divide(tipoCambioPenUsd, 2, RoundingMode.HALF_UP);
        }
        if ("CLP".equals(moneda)) {
            return p.getTotal().divide(tipoCambioClpUsd, 2, RoundingMode.HALF_UP);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "El pago con PayPal no está disponible para pedidos en " + moneda);
    }

    private Map<String, Object> consultarOrdenPayPal(String idOrden) {
        try {
            Map<String, Object> orden = payPal.consultarOrden(idOrden);
            if (orden == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
            }
            return orden;
        } catch (RestClientResponseException e) {
            log.warn("PayPal respondió {} al consultar la orden {}", e.getStatusCode().value(), idOrden);
            if (e.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "No se encontró ese pago en PayPal");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
        } catch (RestClientException e) {
            log.error("No se pudo conectar con PayPal: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
        }
    }

    private ApiException falloPayPal(String accion, RestClientException e) {
        if (e instanceof RestClientResponseException r) {
            log.error("PayPal respondió {} al {}: {}", r.getStatusCode().value(), accion,
                    recortar(r.getResponseBodyAsString()));
        } else {
            log.error("No se pudo conectar con PayPal al {}: {}", accion, e.getMessage());
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo completar el pago con PayPal");
    }

    /** El "href" del enlace con ese "rel" (por ejemplo payer-action: la página de pago de PayPal). */
    private static String enlace(Map<String, Object> orden, String rel) {
        if (orden == null || !(orden.get("links") instanceof List<?> enlaces)) {
            return null;
        }
        for (Object o : enlaces) {
            Map<String, Object> l = mapa(o);
            if (rel.equals(texto(l.get("rel")))) {
                return texto(l.get("href"));
            }
        }
        return null;
    }

    private static Map<String, Object> primeraUnidad(Map<String, Object> orden) {
        return primero(orden == null ? null : orden.get("purchase_units"));
    }

    private static Map<String, Object> primeraCaptura(Map<String, Object> captura) {
        Map<String, Object> pagos = mapa(primeraUnidad(captura).get("payments"));
        return primero(pagos.get("captures"));
    }

    private static Map<String, Object> primero(Object lista) {
        return lista instanceof List<?> l && !l.isEmpty() ? mapa(l.get(0)) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String recortar(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    // ------------------------------------------------------------------ apoyo

    /** El pedido solo si es de la tienda del token Y del cliente del token; si no, 404. */
    private Pedido pedidoDelCliente(UsuarioAutenticado yo, Long idPedido) {
        return pedidoRepository.findByIdPedidoAndIdTienda(idPedido, yo.idTienda())
                .filter(p -> yo.idLogin().equals(p.getIdLogin()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pedido no existe"));
    }

    private static ResultadoPagoResponse resultado(Pedido p, Short estadoPago) {
        return new ResultadoPagoResponse(p.getIdPedido(), p.getNumeroPedido(),
                PedidoResumenResponse.nombreEstadoPago(estadoPago));
    }

    private static String texto(Object o) {
        return (o == null || o.toString().isBlank()) ? null : o.toString();
    }

    private static BigDecimal numero(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String recortar(String s) {
        return s != null && s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
