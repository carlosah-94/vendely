package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.OpcionPlanResponse;
import curso.fullstack.shopify.dto.PagoPlanIniciadoResponse;
import curso.fullstack.shopify.dto.SuscripcionResponse;
import curso.fullstack.shopify.entity.Plan;
import curso.fullstack.shopify.entity.Suscripcion;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.PlanRepository;
import curso.fullstack.shopify.repository.SuscripcionRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Pago del plan de una tienda con PayPal Sandbox (en dolares) y su historial.
 *
 * Flujo: 1) iniciar crea la suscripcion PENDIENTE y la orden en PayPal; el dueno paga alla.
 * 2) PayPal lo devuelve al panel y confirmar verifica la orden EN PayPal (nunca se cree al
 * navegador), la cobra y guarda el periodo pagado. 3) Si cancela en PayPal, el plan no cambia.
 *
 * Reglas (como los SaaS: Claude, Shopify):
 * - SUBIR de plan: empieza hoy; se cobra el precio menos un credito por los dias no usados.
 * - BAJAR de plan: empieza cuando termina lo que ya pago (no pierde dias pagados).
 * - RENOVAR el mismo: los 30 dias se suman al final.
 * El plan de cada tienda sale de su historial de pagos (sincronizarPlanes): cada dia, al
 * arrancar y despues de cada pago. Sin pago vigente, vuelve a Gratis sin borrar nada.
 */
@Slf4j
@Service
public class SuscripcionService {

    private static final int DIAS_POR_PAGO = 30;

    private final VerificadorTienda verificadorTienda;
    private final TiendaRepository tiendaRepository;
    private final PlanRepository planRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PayPalClient payPal;
    private final String frontendUrl;

    public SuscripcionService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
                              PlanRepository planRepository, SuscripcionRepository suscripcionRepository,
                              PayPalClient payPal,
                              @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.verificadorTienda = verificadorTienda;
        this.tiendaRepository = tiendaRepository;
        this.planRepository = planRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.payPal = payPal;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    // ------------------------------------------------------------------ 1. iniciar el pago

    @Transactional
    public PagoPlanIniciadoResponse iniciar(UsuarioAutenticado yo, Long idTienda, Short idPlan) {
        Tienda tienda = verificadorTienda.delDueno(yo, idTienda);
        Plan plan = planRepository.findById(idPlan)
                .filter(p -> p.getEstado() != null && p.getEstado() == 1)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El plan no existe"));
        if (plan.esGratis()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El plan Gratis no se paga");
        }
        if (!payPal.estaConfigurado()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El pago con PayPal no esta disponible por ahora");
        }

        // Se guarda primero (pendiente) para tener su id y ponerlo en la URL de regreso.
        OffsetDateTime ahora = OffsetDateTime.now();
        OpcionPlanResponse opcion = opcion(tienda, plan, ahora);
        Suscripcion s = new Suscripcion();
        s.setIdTienda(idTienda);
        s.setIdPlan(plan.getIdPlan());
        s.setFechaInicio(ahora);                       // provisional: se fija al confirmar
        s.setFechaFin(ahora.plusDays(DIAS_POR_PAGO));
        s.setMontoUsd(opcion.montoUsd());              // precio de hoy, menos el credito si sube de plan
        s.setEstado(Suscripcion.PENDIENTE);
        suscripcionRepository.saveAndFlush(s);

        String retorno = frontendUrl + "/panel/plan?suscripcion=" + s.getIdSuscripcion();
        Map<String, Object> orden;
        try {
            orden = payPal.crearOrden(cuerpoOrden(s, plan, tienda, retorno));
        } catch (RestClientException e) {
            throw fallo("crear la orden", e);            // @Transactional deshace la suscripcion pendiente
        }
        String idOrden = orden == null ? null : texto(orden.get("id"));
        String url = enlace(orden, "payer-action");
        if (url == null) {
            url = enlace(orden, "approve");
        }
        if (idOrden == null || url == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago con PayPal");
        }
        s.setReferenciaPago(idOrden);
        suscripcionRepository.save(s);
        log.info("Pago de plan iniciado: tienda={}, plan={} ({}), suscripcion={}, {} USD (credito {})",
                idTienda, plan.getNombre(), opcion.tipo(), s.getIdSuscripcion(), opcion.montoUsd(), opcion.creditoUsd());
        return new PagoPlanIniciadoResponse(s.getIdSuscripcion(), url);
    }

    // ------------------------------------------------------------------ 2. confirmar al volver de PayPal

    @Transactional
    public SuscripcionResponse confirmar(UsuarioAutenticado yo, Long idTienda, Long idSuscripcion) {
        Tienda tienda = verificadorTienda.delDueno(yo, idTienda);
        Suscripcion s = suscripcionRepository.bloquear(idSuscripcion, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pago de plan no existe"));
        if (s.getEstado() != Suscripcion.PENDIENTE || s.getReferenciaPago() == null) {
            return responder(s);                           // ya se proceso (por ejemplo, recargo la pagina)
        }
        return procesar(tienda, s);
    }

    // ------------------------------------------------------------------ conciliacion (sin el dueno)

    /**
     * Cierra un pago de plan que quedo PENDIENTE porque el dueno no volvio de PayPal (cerro la
     * pestana, se le corto la conexion...). Lo llama ConciliacionPagosService cada 15 minutos
     * con los que llevan mas de 30 minutos: le pregunta a PayPal como termino la orden y hace lo
     * mismo que si el dueno hubiera vuelto. Si la cobro, el plan se activa; si no la pago (o la
     * orden ya no existe en PayPal), pasa a CANCELADA y deja de verse "Pendiente".
     *
     * @return true si la cerro (pagada o cancelada); false si sigue pendiente o ya estaba cerrada
     */
    @Transactional
    public boolean conciliar(Long idSuscripcion) {
        if (!payPal.estaConfigurado()) {
            return false;                                  // sin PayPal no hay a quien preguntar
        }
        Suscripcion s = suscripcionRepository.findById(idSuscripcion)
                .flatMap(p -> suscripcionRepository.bloquear(idSuscripcion, p.getIdTienda()))
                .orElse(null);
        if (s == null || s.getEstado() != Suscripcion.PENDIENTE) {
            return false;                                  // el dueno volvio mientras tanto
        }
        if (s.getReferenciaPago() == null) {
            cancelar(s, "nunca se creo la orden en PayPal");
            return true;
        }
        Tienda tienda = tiendaRepository.findById(s.getIdTienda()).orElseThrow();
        procesar(tienda, s);
        return s.getEstado() != Suscripcion.PENDIENTE;
    }

    /**
     * Pregunta a PayPal por la orden de la suscripcion y la cierra: la cobra si el dueno la
     * aprobo, la activa si ya estaba cobrada o la cancela si no la pago. Lo usan confirmar
     * (el dueno volvio de PayPal) y conciliar (no volvio).
     */
    private SuscripcionResponse procesar(Tienda tienda, Suscripcion s) {
        Long idSuscripcion = s.getIdSuscripcion();
        Map<String, Object> orden = consultar(s.getReferenciaPago());
        if (orden == null) {
            // PayPal ya no la tiene: las ordenes que nadie aprueba se borran a las pocas horas.
            return cancelar(s, "la orden ya no existe en PayPal");
        }
        Map<String, Object> unidad = primero(orden.get("purchase_units"));
        Map<String, Object> monto = mapa(unidad.get("amount"));
        BigDecimal valor = numero(monto.get("value"));
        if (!("SUS-" + s.getIdSuscripcion()).equals(texto(unidad.get("custom_id")))
                || !"USD".equals(texto(monto.get("currency_code")))
                || valor == null || valor.compareTo(s.getMontoUsd()) != 0) {
            log.warn("Orden de PayPal {} no corresponde a la suscripcion {}", s.getReferenciaPago(), idSuscripcion);
            throw new ApiException(HttpStatus.BAD_REQUEST, "El pago no corresponde a este plan");
        }

        String estadoOrden = texto(orden.get("status"));
        if ("COMPLETED".equals(estadoOrden)) {
            return activar(tienda, s);
        }
        if (!"APPROVED".equals(estadoOrden)) {
            return cancelar(s, "estado de la orden " + estadoOrden);   // no aprobo el pago en PayPal
        }
        Map<String, Object> captura;
        try {
            captura = payPal.capturarOrden(s.getReferenciaPago());
        } catch (RestClientResponseException e) {
            if (e.getResponseBodyAsString().contains("ORDER_ALREADY_CAPTURED")) {
                return activar(tienda, s);
            }
            if (e.getStatusCode().value() == 422) {
                return cancelar(s, "PayPal rechazo el cobro");
            }
            throw fallo("cobrar la orden", e);
        } catch (RestClientException e) {
            throw fallo("cobrar la orden", e);
        }
        Map<String, Object> pagos = mapa(primero(captura == null ? null : captura.get("purchase_units")).get("payments"));
        String estadoCobro = texto(primero(pagos.get("captures")).get("status"));
        if ("COMPLETED".equals(estadoCobro)) {
            return activar(tienda, s);
        }
        return cancelar(s, "estado del cobro " + estadoCobro);
    }

    /** El dueno cancelo en PayPal: la suscripcion queda cancelada y el plan no cambia. */
    @Transactional
    public SuscripcionResponse cancelarPorUsuario(UsuarioAutenticado yo, Long idTienda, Long idSuscripcion) {
        verificadorTienda.delDueno(yo, idTienda);
        Suscripcion s = suscripcionRepository.bloquear(idSuscripcion, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pago de plan no existe"));
        if (s.getEstado() != Suscripcion.PENDIENTE) {
            return responder(s);
        }
        return cancelar(s, "cancelado por el dueno");
    }

    // ------------------------------------------------------------------ historial

    @Transactional(readOnly = true)
    public List<SuscripcionResponse> historial(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.delDueno(yo, idTienda);
        Map<Short, Plan> planes = planRepository.findAll().stream()
                .collect(Collectors.toMap(Plan::getIdPlan, p -> p));
        return suscripcionRepository.findByIdTiendaOrderByFechaCreacionDesc(idTienda).stream()
                .map(s -> SuscripcionResponse.desde(s, planes.get(s.getIdPlan()))).toList();
    }

    @Transactional(readOnly = true)
    public SuscripcionResponse ver(UsuarioAutenticado yo, Long idTienda, Long idSuscripcion) {
        verificadorTienda.delDueno(yo, idTienda);
        return responder(buscar(idTienda, idSuscripcion));
    }

    // ------------------------------------------------------------------ opciones para la pantalla Plan

    /** Que pasaria con cada plan pagado si el dueno lo elige hoy (precio, credito y desde cuando). */
    @Transactional(readOnly = true)
    public List<OpcionPlanResponse> opciones(Long idTienda) {
        Tienda tienda = tiendaRepository.findById(idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        OffsetDateTime ahora = OffsetDateTime.now();
        List<OpcionPlanResponse> lista = new ArrayList<>();
        for (Plan p : planRepository.findAllByOrderByPrecioUsdAsc()) {
            if (!p.esGratis() && p.getEstado() != null && p.getEstado() == 1) {
                lista.add(opcion(tienda, p, ahora));
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------ 3. plan de cada tienda segun sus pagos

    /** Todos los dias a las 3:00 a. m. (hora de Lima). */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Lima")
    @Transactional
    public void sincronizarPlanes() {
        int n = tiendaRepository.sincronizarPlanes();
        if (n > 0) {
            log.info("Planes actualizados segun sus pagos: {} tienda(s) (vencidos o cambios programados)", n);
        }
    }

    /** Tambien al arrancar el backend, por si estuvo apagado a las 3:00 a. m. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sincronizarAlArrancar() {
        int n = tiendaRepository.sincronizarPlanes();
        if (n > 0) {
            log.info("Planes actualizados al arrancar: {} tienda(s) (vencidos o cambios programados)", n);
        }
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * Guarda el periodo pagado y actualiza el plan de la tienda.
     * SUBIR: empieza hoy; lo que quedaba de los planes anteriores termina hoy (ya se desconto como credito).
     * BAJAR o RENOVAR: empieza cuando termina el ultimo periodo ya pagado.
     */
    private SuscripcionResponse activar(Tienda tienda, Suscripcion s) {
        OffsetDateTime ahora = OffsetDateTime.now();
        Plan nuevo = planRepository.findById(s.getIdPlan()).orElseThrow();
        String tipo = tipo(tienda, nuevo);
        OffsetDateTime inicio;
        if ("SUBIR".equals(tipo)) {
            suscripcionRepository.terminarVigente(tienda.getIdTienda(), ahora);
            suscripcionRepository.cancelarFuturas(tienda.getIdTienda(), ahora);
            inicio = ahora;
        } else {
            inicio = finDeLoPagado(tienda.getIdTienda(), ahora);
        }
        s.setFechaInicio(inicio);
        s.setFechaFin(inicio.plusDays(DIAS_POR_PAGO));
        s.setEstado(Suscripcion.PAGADA);
        suscripcionRepository.saveAndFlush(s);

        tiendaRepository.sincronizarPlan(tienda.getIdTienda());
        log.info("Plan pagado ({}): tienda={}, plan={}, del {} al {}",
                tipo, tienda.getIdTienda(), nuevo.getNombre(), s.getFechaInicio(), s.getFechaFin());
        return responder(s);
    }

    /** SUBIR, BAJAR o RENOVAR, comparando con el plan que la tienda tiene hoy. */
    private String tipo(Tienda tienda, Plan nuevo) {
        if (nuevo.getIdPlan().equals(tienda.getIdPlan())) {
            return "RENOVAR";
        }
        BigDecimal precioActual = planRepository.findById(tienda.getIdPlan()).map(Plan::getPrecioUsd).orElse(BigDecimal.ZERO);
        return nuevo.getPrecioUsd().compareTo(precioActual) > 0 ? "SUBIR" : "BAJAR";
    }

    /** Precio, credito y fecha de inicio si la tienda eligiera este plan ahora. */
    private OpcionPlanResponse opcion(Tienda tienda, Plan plan, OffsetDateTime ahora) {
        String tipo = tipo(tienda, plan);
        BigDecimal precio = plan.getPrecioUsd().setScale(2, RoundingMode.HALF_UP);
        BigDecimal credito = BigDecimal.ZERO.setScale(2);
        OffsetDateTime empieza;
        if ("SUBIR".equals(tipo)) {
            credito = credito(tienda.getIdTienda(), ahora);
            // Siempre se cobra algo (la base exige monto > 0): como maximo se descuenta el precio menos 1 dolar.
            BigDecimal tope = precio.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
            credito = credito.min(tope);
            empieza = ahora;
        } else {
            empieza = finDeLoPagado(tienda.getIdTienda(), ahora);
        }
        return new OpcionPlanResponse(plan.getIdPlan(), tipo, precio, credito, precio.subtract(credito), empieza);
    }

    /**
     * Credito por lo pagado que todavia no se usa: de cada periodo pagado que no termino,
     * la parte proporcional a los dias que faltan. Ejemplo: Basico (15 USD) con 10 de 30 dias
     * sin usar = 5 USD de credito.
     */
    private BigDecimal credito(Long idTienda, OffsetDateTime ahora) {
        BigDecimal total = BigDecimal.ZERO;
        for (Suscripcion p : pagadasVigentes(idTienda, ahora)) {
            long duracion = Duration.between(p.getFechaInicio(), p.getFechaFin()).getSeconds();
            OffsetDateTime desde = p.getFechaInicio().isAfter(ahora) ? p.getFechaInicio() : ahora;
            long sinUsar = Duration.between(desde, p.getFechaFin()).getSeconds();
            if (duracion > 0 && sinUsar > 0) {
                total = total.add(p.getMontoUsd().multiply(BigDecimal.valueOf(sinUsar))
                        .divide(BigDecimal.valueOf(duracion), 2, RoundingMode.DOWN));
            }
        }
        return total.setScale(2, RoundingMode.DOWN);
    }

    /** Cuando termina lo ya pagado (el periodo actual y los programados); ahora si no hay nada. */
    private OffsetDateTime finDeLoPagado(Long idTienda, OffsetDateTime ahora) {
        return pagadasVigentes(idTienda, ahora).stream()
                .map(Suscripcion::getFechaFin)
                .max(OffsetDateTime::compareTo)
                .orElse(ahora);
    }

    private List<Suscripcion> pagadasVigentes(Long idTienda, OffsetDateTime ahora) {
        return suscripcionRepository.findByIdTiendaAndEstadoAndFechaFinAfterOrderByFechaInicioAsc(
                idTienda, Suscripcion.PAGADA, ahora);
    }

    private SuscripcionResponse cancelar(Suscripcion s, String motivo) {
        s.setEstado(Suscripcion.CANCELADA);
        suscripcionRepository.saveAndFlush(s);
        log.info("Pago de plan no completado: suscripcion={} ({})", s.getIdSuscripcion(), motivo);
        return responder(s);
    }

    private Suscripcion buscar(Long idTienda, Long idSuscripcion) {
        return suscripcionRepository.findByIdSuscripcionAndIdTienda(idSuscripcion, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pago de plan no existe"));
    }

    private SuscripcionResponse responder(Suscripcion s) {
        return SuscripcionResponse.desde(s, planRepository.findById(s.getIdPlan()).orElse(null));
    }

    private static Map<String, Object> cuerpoOrden(Suscripcion s, Plan plan, Tienda tienda, String retorno) {
        Map<String, Object> monto = new LinkedHashMap<>();
        monto.put("currency_code", "USD");
        monto.put("value", s.getMontoUsd().setScale(2).toPlainString());

        Map<String, Object> unidad = new LinkedHashMap<>();
        unidad.put("reference_id", "SUS-" + s.getIdSuscripcion());
        unidad.put("custom_id", "SUS-" + s.getIdSuscripcion());
        unidad.put("description", recortar("Plan " + plan.getNombre() + " de Vendely (30 dias) - " + tienda.getNombre(), 120));
        unidad.put("amount", monto);

        Map<String, Object> experiencia = new LinkedHashMap<>();
        experiencia.put("brand_name", "Vendely");
        experiencia.put("shipping_preference", "NO_SHIPPING");
        experiencia.put("user_action", "PAY_NOW");
        experiencia.put("return_url", retorno);
        experiencia.put("cancel_url", retorno + "&cancelado=1");

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("intent", "CAPTURE");
        cuerpo.put("purchase_units", List.of(unidad));
        cuerpo.put("payment_source", Map.of("paypal", Map.of("experience_context", experiencia)));
        return cuerpo;
    }

    /** La orden tal como la tiene PayPal, o null si PayPal responde 404 (ya no existe). */
    private Map<String, Object> consultar(String idOrden) {
        try {
            Map<String, Object> orden = payPal.consultarOrden(idOrden);
            if (orden == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
            }
            return orden;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.info("La orden {} ya no existe en PayPal", idOrden);
                return null;
            }
            log.warn("PayPal respondio {} al consultar la orden {}", e.getStatusCode().value(), idOrden);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
        } catch (RestClientException e) {
            log.error("No se pudo conectar con PayPal: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el pago");
        }
    }

    private static ApiException fallo(String accion, RestClientException e) {
        if (e instanceof RestClientResponseException r) {
            log.error("PayPal respondio {} al {} (plan)", r.getStatusCode().value(), accion);
        } else {
            log.error("No se pudo conectar con PayPal al {} (plan): {}", accion, e.getMessage());
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo completar el pago con PayPal");
    }

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

    private static Map<String, Object> primero(Object lista) {
        return lista instanceof List<?> l && !l.isEmpty() ? mapa(l.get(0)) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String texto(Object o) {
        return (o == null || o.toString().isBlank()) ? null : o.toString();
    }

    private static BigDecimal numero(Object o) {
        try {
            return o == null ? null : new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String recortar(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
