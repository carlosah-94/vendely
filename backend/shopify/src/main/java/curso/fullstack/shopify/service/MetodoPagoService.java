package curso.fullstack.shopify.service;

import curso.fullstack.shopify.exception.ApiException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Qué métodos de pago ofrece cada tienda.
 *
 * Dos condiciones, y tienen que cumplirse las dos:
 *   1. El SERVIDOR tiene configurada la pasarela (sus credenciales están en las variables de
 *      entorno). Una pasarela sin credenciales no se ofrece a nadie: fallaría al pagar.
 *   2. El PLAN de la tienda la incluye. El plan Gratis incluye solo el método base (el primero
 *      configurado, normalmente PayPal); Básico y Pro, todos (FuncionPlan.METODOS_PAGO).
 *
 * Que el método base sea "el primero configurado" y no "PayPal" a secas es a propósito: si el
 * servidor solo tiene Mercado Pago, una tienda Gratis igual puede cobrar. Es lo mismo que pasaba
 * antes de que existiera la elección, cuando el backend tomaba la primera pasarela disponible.
 *
 * Lo usan PagoService (para validar el método que pide el cliente) y TiendaService (para
 * decirle al frontend qué botones mostrar), así las dos partes aplican siempre la misma regla.
 */
@Slf4j
@Service
public class MetodoPagoService {

    private final PayPalClient payPal;
    private final MercadoPagoClient mercadoPago;
    private final PlanService planService;

    public MetodoPagoService(PayPalClient payPal, MercadoPagoClient mercadoPago, PlanService planService) {
        this.payPal = payPal;
        this.mercadoPago = mercadoPago;
        this.planService = planService;
    }

    /** Los métodos que la tienda ofrece hoy, en orden (el primero es el que se usa por defecto). */
    @Transactional(readOnly = true)
    public List<MetodoPago> disponibles(Long idTienda) {
        List<MetodoPago> configurados = configurados();
        if (configurados.size() <= 1 || planService.permite(idTienda, FuncionPlan.METODOS_PAGO)) {
            return configurados;
        }
        return List.of(configurados.get(0));   // plan sin "más métodos de pago": solo el método base
    }

    /**
     * El método con el que se va a cobrar. Si el cliente no eligió (el frontend antiguo no manda
     * nada), se usa el primero disponible. Errores, cada uno con su código:
     *   503  ninguna pasarela configurada en el servidor
     *   409  la pasarela pedida no está configurada en el servidor
     *   403  el plan de la tienda no la incluye ("Mejora tu plan", lo reconoce el frontend)
     */
    @Transactional(readOnly = true)
    public MetodoPago elegir(Long idTienda, MetodoPago pedido) {
        List<MetodoPago> configurados = configurados();
        if (configurados.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El pago en línea no está disponible por ahora");
        }
        if (pedido == null) {
            return disponibles(idTienda).get(0);
        }
        if (!configurados.contains(pedido)) {
            throw new ApiException(HttpStatus.CONFLICT, "El pago con " + pedido.nombre() + " no está disponible por ahora");
        }
        if (!disponibles(idTienda).contains(pedido)) {
            // Configurado en el servidor, pero el plan de la tienda solo incluye el método base.
            log.info("Método de pago bloqueado por el plan: tienda={}, metodo={}", idTienda, pedido);
            planService.exigir(idTienda, FuncionPlan.METODOS_PAGO);
        }
        return pedido;
    }

    /** Las pasarelas con credenciales en el servidor, en el orden del enum. */
    private List<MetodoPago> configurados() {
        List<MetodoPago> lista = new ArrayList<>();
        if (payPal.estaConfigurado()) {
            lista.add(MetodoPago.PAYPAL);
        }
        if (mercadoPago.estaConfigurado()) {
            lista.add(MetodoPago.MERCADO_PAGO);
        }
        return lista;
    }
}
