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
 * Pago de un pedido con Mercado Pago (Checkout Pro, modo de prueba).
 *
 * Regla de oro: NUNCA se cree lo que manda el navegador. La URL de regreso trae
 * status=approved, pero cualquiera puede escribirla a mano: siempre se consulta el pago
 * en Mercado Pago con el paymentId y se comparan estado, pedido y monto.
 * En los logs no se escribe ningún dato de tarjeta (nunca llegan a este servidor).
 */
@Slf4j
@Service
public class PagoService {

    private final PedidoRepository pedidoRepository;
    private final PagoRepository pagoRepository;
    private final TiendaRepository tiendaRepository;
    private final MercadoPagoClient mercadoPago;
    private final String frontendUrl;

    public PagoService(PedidoRepository pedidoRepository, PagoRepository pagoRepository,
                       TiendaRepository tiendaRepository, MercadoPagoClient mercadoPago,
                       @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.pedidoRepository = pedidoRepository;
        this.pagoRepository = pagoRepository;
        this.tiendaRepository = tiendaRepository;
        this.mercadoPago = mercadoPago;
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
        if (!mercadoPago.estaConfigurado()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El pago en línea no está disponible por ahora");
        }
        Tienda tienda = tiendaRepository.findById(p.getIdTienda())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));

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
            return resultado(p, existente.get().getEstado());
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
