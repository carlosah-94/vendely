package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.PagoIniciadoResponse;
import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.dto.ResultadoPagoResponse;
import curso.fullstack.shopify.entity.Pago;
import curso.fullstack.shopify.entity.Pedido;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.evento.PagoAprobadoEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Pago de un pedido con una pasarela en modo de prueba: PayPal (Sandbox) o Mercado Pago (Checkout Pro).
 * El cliente elige con cuál; qué métodos ofrece cada tienda lo decide MetodoPagoService (las
 * pasarelas configuradas en el servidor y el plan de la tienda).
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
    private final PlanService planService;
    private final MetodoPagoService metodoPagoService;
    private final ApplicationEventPublisher eventos;
    private final BigDecimal tipoCambioPenUsd;
    private final BigDecimal tipoCambioClpUsd;
    private final String frontendUrl;
    private final int diasParaVencer;

    public PagoService(PedidoRepository pedidoRepository, PagoRepository pagoRepository,
                       TiendaRepository tiendaRepository, MercadoPagoClient mercadoPago, PayPalClient payPal,
                       PlanService planService, MetodoPagoService metodoPagoService,
                       ApplicationEventPublisher eventos,
                       @Value("${app.paypal.tipo-cambio-pen-usd:3.75}") BigDecimal tipoCambioPenUsd,
                       @Value("${app.paypal.tipo-cambio-clp-usd:950}") BigDecimal tipoCambioClpUsd,
                       @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                       @Value("${app.pagos.dias-para-vencer:3}") int diasParaVencer) {
        this.diasParaVencer = diasParaVencer;
        this.pedidoRepository = pedidoRepository;
        this.pagoRepository = pagoRepository;
        this.tiendaRepository = tiendaRepository;
        this.mercadoPago = mercadoPago;
        this.payPal = payPal;
        this.planService = planService;
        this.metodoPagoService = metodoPagoService;
        this.eventos = eventos;
        this.tipoCambioPenUsd = tipoCambioPenUsd;
        this.tipoCambioClpUsd = tipoCambioClpUsd;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    // ------------------------------------------------------------------ iniciar el pago

    /**
     * Inicia el pago del pedido con la pasarela que eligió el cliente.
     *
     * Antes el backend decidía solo (PayPal si estaba configurado; si no, Mercado Pago). Ahora el
     * cliente elige, y MetodoPagoService comprueba que ese método esté configurado en el servidor
     * y que el plan de la tienda lo incluya. Si no elige (metodo null, como el frontend antiguo),
     * se usa el primero disponible: el comportamiento de siempre.
     *
     * Si la pasarela falla se lanza ApiException: @Transactional deshace el registro de pago.
     */
    @Transactional
    public PagoIniciadoResponse iniciar(UsuarioAutenticado yo, Long idPedido, MetodoPago metodo) {
        return iniciar(pedidoDelCliente(yo, idPedido), metodo);
    }

    /** Lo mismo para el pedido de un invitado (su clave ya la revisó el controlador). */
    @Transactional
    public PagoIniciadoResponse iniciarInvitado(Long idTienda, Long idPedido, MetodoPago metodo) {
        return iniciar(pedidoDeInvitado(idTienda, idPedido), metodo);
    }

    private PagoIniciadoResponse iniciar(Pedido p, MetodoPago metodo) {
        Long idPedido = p.getIdPedido();
        if (pagoRepository.existsByIdPedidoAndEstado(idPedido, Pago.APROBADO)) {
            throw new ApiException(HttpStatus.CONFLICT, "El pedido #" + p.getNumeroPedido() + " ya está pagado");
        }
        Tienda tienda = tiendaRepository.findById(p.getIdTienda())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        return switch (metodoPagoService.elegir(p.getIdTienda(), metodo)) {
            case PAYPAL -> iniciarPayPal(p, tienda);
            case MERCADO_PAGO -> iniciarMercadoPago(p, tienda);
        };
    }

    /** Crea el intento de pago (pendiente) y la preferencia de Checkout Pro en Mercado Pago. */
    private PagoIniciadoResponse iniciarMercadoPago(Pedido p, Tienda tienda) {
        Long idPedido = p.getIdPedido();
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
        return aplicarPago(pedidoDelCliente(yo, idPedido), paymentId, null);
    }

    /** Confirmación del pago de un invitado: el mismo camino, sin cuenta. */
    @Transactional
    public ResultadoPagoResponse confirmarInvitado(Long idTienda, Long idPedido, String paymentId) {
        return aplicarPago(pedidoDeInvitado(idTienda, idPedido), paymentId, null);
    }

    /**
     * El núcleo que comparten las DOS entradas: el regreso del navegador (confirmar) y el aviso
     * de la pasarela (aplicarAvisoMercadoPago). Tener un solo camino es lo que mantiene la
     * idempotencia: un pedido que ya tiene pago aprobado no se vuelve a tocar, venga el aviso
     * dos veces o vuelva el cliente a recargar la pantalla.
     *
     * pagoEnMp llega ya consultado cuando el aviso tuvo que leerlo antes para saber de qué pedido
     * es; si viene null, se consulta aquí. En ningún caso se cree lo que diga el navegador o el
     * aviso: lo que vale es el pago consultado en Mercado Pago.
     */
    private ResultadoPagoResponse aplicarPago(Pedido p, String paymentId, Map<String, Object> pagoEnMp) {
        Long idPedido = p.getIdPedido();

        // Ya aprobado antes (por ejemplo, el cliente recargó la página): no se cobra ni se cambia nada.
        // Con el pedido tomado, para que dos entradas a la vez no lo aprueben (ni avisen) dos veces.
        if (yaPagado(idPedido)) {
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

        Map<String, Object> mp = pagoEnMp != null ? pagoEnMp : consultarPago(paymentId);

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
            guardarComision(p);
            eventos.publishEvent(new PagoAprobadoEvent(p.getIdTienda(), p.getIdPedido()));
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

    /**
     * Guarda en el pedido la comisión que Vendely cobra por esta venta (issue #3).
     *
     * Se calcula al aprobarse el pago y con el plan que la tienda tiene EN ESE momento: si
     * después sube o baja de plan, lo que ya se cobró no cambia. Va en la misma transacción
     * que la aprobación del pago, así que o quedan las dos cosas o no queda ninguna.
     */
    private void guardarComision(Pedido p) {
        if (p.getComisionPlataforma() != null) {
            return;   // ya estaba calculada: el aviso de la pasarela y el regreso del navegador coincidieron
        }
        BigDecimal porcentaje = planService.comision(p.getIdTienda());
        BigDecimal comision = p.getTotal()
                .multiply(porcentaje)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        p.setComisionPlataforma(comision);
        pedidoRepository.save(p);
        log.info("Comisión de Vendely: pedido #{}, {} % de {} {} = {}", p.getNumeroPedido(),
                porcentaje, p.getTotal(), p.getMoneda(), comision);
    }

    // ------------------------------------------------------------------ avisos de la pasarela (webhook)

    /**
     * Aviso de Mercado Pago. Aquí no hay cliente con sesión, así que el pedido se ubica por el
     * external_reference; y ese dato no se lee del aviso (que trae solo un id), sino del pago
     * consultado en Mercado Pago.
     *
     * Si el aviso no corresponde a un pedido de Vendely se registra y se ignora: la pasarela
     * manda avisos de prueba y de otros recursos, y ninguno debe hacer fallar la petición.
     */
    @Transactional
    public void aplicarAvisoMercadoPago(String paymentId) {
        Map<String, Object> mp = consultarPago(paymentId);
        Long idPedido = aLong(texto(mp.get("external_reference")));
        if (idPedido == null) {
            log.info("Aviso de Mercado Pago del pago {} sin external_reference: no es de un pedido", paymentId);
            return;
        }
        Pedido p = pedidoRepository.findById(idPedido).orElse(null);
        if (p == null) {
            log.warn("Aviso de Mercado Pago del pago {}: el pedido {} no existe", paymentId, idPedido);
            return;
        }
        aplicarPago(p, paymentId, mp);
    }

    /**
     * Aviso de PayPal. Al iniciar el pago, el id de la orden quedó guardado como referencia del
     * intento, así que de ahí sale el pedido. Después se verifica y se cobra la orden EN PayPal,
     * por el mismo camino que usa el regreso del navegador (confirmarPayPal).
     *
     * @param idOrden   id de la orden de PayPal; puede ser null en los avisos de cobro
     * @param idPedido  custom_id del aviso: el respaldo cuando no viene el id de la orden
     */
    @Transactional
    public void aplicarAvisoPayPal(String idOrden, Long idPedido) {
        Pago pago = idOrden == null ? null : pagoRepository.findByReferenciaTransaccion(idOrden).orElse(null);
        if (pago == null && idPedido != null) {
            pago = pagoRepository.findFirstByIdPedidoAndEstadoOrderByIdPagoDesc(idPedido, Pago.PENDIENTE)
                    .filter(x -> Pago.PAYPAL == x.getProveedor())
                    .orElse(null);
        }
        if (pago == null) {
            log.info("Aviso de PayPal (orden {}) que no corresponde a ningún pago de Vendely", idOrden);
            return;
        }
        if (Pago.APROBADO == pago.getEstado()) {
            return;   // el mismo aviso llegó dos veces: no se cobra de nuevo
        }
        Pedido p = pedidoRepository.findById(pago.getIdPedido()).orElse(null);
        if (p == null) {
            log.warn("Aviso de PayPal (orden {}): el pedido {} no existe", idOrden, pago.getIdPedido());
            return;
        }
        confirmarPayPal(p, pago);
    }

    // ------------------------------------------------------------------ conciliación de pagos huérfanos

    /**
     * Red de seguridad por si el aviso nunca llega: le pregunta a la pasarela por un intento de
     * pago que quedó pendiente. En local, sin una URL pública donde recibir avisos, es el único
     * camino para que un pedido pagado deje de estar pendiente.
     *
     * Devuelve true si el pago cambió de estado. La llama ConciliacionPagosService cada 15 minutos.
     */
    @Transactional
    public boolean conciliar(Long idPago) {
        Pago pago = pagoRepository.findById(idPago).orElse(null);
        if (pago == null || Pago.PENDIENTE != pago.getEstado()) {
            return false;
        }
        if (pagoRepository.existsByIdPedidoAndEstado(pago.getIdPedido(), Pago.APROBADO)) {
            // Otro intento del mismo pedido ya quedó aprobado: este sobra. Antes se dejaba
            // pendiente y se volvía a revisar cada 15 minutos para siempre.
            return vencer(pago, "el pedido ya se pagó con otro intento");
        }
        Pedido p = pedidoRepository.findById(pago.getIdPedido()).orElse(null);
        if (p == null) {
            return false;
        }
        Short antes = pago.getEstado();

        if (Pago.PAYPAL == pago.getProveedor()) {
            if (pago.getReferenciaTransaccion() == null || !payPal.estaConfigurado()) {
                return false;
            }
            try {
                confirmarPayPal(p, pago);
            } catch (ApiException e) {
                if (e.getStatus() != HttpStatus.NOT_FOUND) {
                    throw e;
                }
                // PayPal ya no tiene la orden (borra a las pocas horas las que nadie aprueba):
                // no se pagó y ya no se puede pagar.
                return vencer(pago, "la orden ya no existe en PayPal");
            }
            return !antes.equals(pago.getEstado());
        }

        // Mercado Pago: el intento pendiente no guardó ningún id (se conoce al confirmar),
        // así que el pago se busca en la pasarela por el número de pedido.
        if (!mercadoPago.estaConfigurado()) {
            return false;
        }
        Map<String, Object> mp = buscarEnMercadoPago(p.getIdPedido());
        String paymentId = mp == null ? null : texto(mp.get("id"));
        if (paymentId == null) {
            // Mercado Pago no tiene ningún pago de este pedido: el cliente fue y no pagó (Mercado
            // Pago no registra nada hasta que se paga). Pasados unos días se deja de preguntar.
            // Si después pagara igual, aplicarPago crea un intento nuevo y el pago no se pierde.
            if (pago.getFechaCreacion() != null
                    && pago.getFechaCreacion().isBefore(OffsetDateTime.now().minusDays(diasParaVencer))) {
                return vencer(pago, "sin pago en Mercado Pago después de " + diasParaVencer + " días");
            }
            return false;
        }
        aplicarPago(p, paymentId, mp);
        return !antes.equals(pago.getEstado());
    }

    /**
     * Cierra un intento de pago que nadie pagó (estado VENCIDO): así deja de revisarse cada
     * 15 minutos y no ocupa lugar en la conciliación. El pedido queda "Sin pago" y se puede
     * volver a pagar. Devuelve true (el intento cambió de estado).
     */
    private boolean vencer(Pago pago, String motivo) {
        pago.setEstado(Pago.VENCIDO);
        guardar(pago);
        log.info("Intento de pago {} del pedido {} vencido: {}", pago.getIdPago(), pago.getIdPedido(), motivo);
        return true;
    }

    /**
     * El pago que Mercado Pago tiene registrado para ese pedido: el aprobado si existe y, si no,
     * el más reciente. Un error de red no interrumpe la conciliación de los demás pagos.
     */
    private Map<String, Object> buscarEnMercadoPago(Long idPedido) {
        try {
            Map<String, Object> r = mercadoPago.buscarPagosDelPedido(String.valueOf(idPedido));
            if (r == null || !(r.get("results") instanceof List<?> resultados) || resultados.isEmpty()) {
                return null;
            }
            Map<String, Object> masReciente = null;
            for (Object o : resultados) {
                Map<String, Object> pago = mapa(o);
                if ("approved".equals(texto(pago.get("status")))) {
                    return pago;
                }
                if (masReciente == null) {
                    masReciente = pago;
                }
            }
            return masReciente;
        } catch (RestClientException e) {
            log.warn("No se pudo buscar en Mercado Pago el pago del pedido {}: {}", idPedido, e.getMessage());
            return null;
        }
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
        // El regreso del navegador y el aviso de PayPal suelen llegar casi juntos: el primero toma
        // el pedido y lo cobra; el segundo espera, ve el pago aprobado y no hace nada más. Antes los
        // dos lo aprobaban y el cliente recibía dos correos de "pago confirmado".
        if (yaPagado(p.getIdPedido())) {
            return resultado(p, Pago.APROBADO);
        }
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

    /**
     * ¿El pedido ya tiene un pago aprobado? Antes de mirar, toma el pedido (FOR UPDATE): si otra
     * transacción lo está aprobando en este momento, espera a que termine y ve su resultado.
     */
    private boolean yaPagado(Long idPedido) {
        pedidoRepository.bloquear(idPedido);
        return pagoRepository.existsByIdPedidoAndEstado(idPedido, Pago.APROBADO);
    }

    private ResultadoPagoResponse aprobarPayPal(Pedido p, Pago pago) {
        pago.setEstado(Pago.APROBADO);
        pago.setFechaPago(OffsetDateTime.now());
        guardar(pago);
        guardarComision(p);
        eventos.publishEvent(new PagoAprobadoEvent(p.getIdTienda(), p.getIdPedido()));
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

    /** Pedido sin cuenta (id_login NULL) de esa tienda; el de un cliente no se paga por aquí. */
    private Pedido pedidoDeInvitado(Long idTienda, Long idPedido) {
        return pedidoRepository.findByIdPedidoAndIdTienda(idPedido, idTienda)
                .filter(p -> p.getIdLogin() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pedido no existe"));
    }

    private static ResultadoPagoResponse resultado(Pedido p, Short estadoPago) {
        return new ResultadoPagoResponse(p.getIdPedido(), p.getNumeroPedido(),
                PedidoResumenResponse.nombreEstadoPago(estadoPago));
    }

    private static String texto(Object o) {
        return (o == null || o.toString().isBlank()) ? null : o.toString();
    }

    private static Long aLong(String s) {
        try {
            return s == null ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
