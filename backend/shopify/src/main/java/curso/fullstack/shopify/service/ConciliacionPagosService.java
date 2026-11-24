package curso.fullstack.shopify.service;

import curso.fullstack.shopify.entity.Pago;
import curso.fullstack.shopify.entity.Suscripcion;
import curso.fullstack.shopify.repository.PagoRepository;
import curso.fullstack.shopify.repository.SuscripcionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cierra los pagos que quedaron a medias. Es la red de seguridad del webhook: si el aviso de la
 * pasarela nunca llega (se perdió, el servidor estaba apagado o, en local, no hay una URL pública
 * donde recibirlo), esta tarea le pregunta igual a la pasarela cómo terminó cada pago pendiente.
 *
 * Revisa dos cosas:
 *  - Los pagos de los pedidos de las tiendas (PagoService.conciliar).
 *  - Los pagos de plan de los dueños (SuscripcionService.conciliar): si el dueño cerró la
 *    pestaña de PayPal sin volver, su pago quedaba "Pendiente" para siempre en el historial.
 *    Ahora se activa el plan si PayPal lo cobró, o se cancela si no lo pagó.
 *
 * Solo mira los intentos que ya llevan un rato (30 minutos por defecto): un pago recién iniciado
 * está pendiente porque el cliente todavía está en la pasarela, no porque algo haya fallado.
 *
 * El trabajo de cada pago lo hace un método @Transactional del service: así un pago que falle
 * no deja a medias los demás. La tarea en sí no abre transacción.
 */
@Slf4j
@Service
public class ConciliacionPagosService {

    private final PagoRepository pagoRepository;
    private final PagoService pagoService;
    private final SuscripcionRepository suscripcionRepository;
    private final SuscripcionService suscripcionService;
    private final int edadMinutos;

    public ConciliacionPagosService(PagoRepository pagoRepository, PagoService pagoService,
                                    SuscripcionRepository suscripcionRepository, SuscripcionService suscripcionService,
                                    @Value("${app.pagos.conciliacion-edad-minutos:30}") int edadMinutos) {
        this.pagoRepository = pagoRepository;
        this.pagoService = pagoService;
        this.suscripcionRepository = suscripcionRepository;
        this.suscripcionService = suscripcionService;
        this.edadMinutos = edadMinutos;
    }

    /**
     * Cada 15 minutos (fixedDelay: se cuenta desde que termina la revisión anterior, así dos
     * revisiones nunca se pisan). La primera corre dos minutos después de arrancar, para no
     * competir con el arranque del backend.
     */
    @Scheduled(fixedDelayString = "${app.pagos.conciliacion-cada-ms:900000}",
               initialDelayString = "${app.pagos.conciliacion-espera-inicial-ms:120000}")
    public void conciliarPendientes() {
        OffsetDateTime limite = OffsetDateTime.now().minusMinutes(edadMinutos);
        conciliarPedidos(limite);
        conciliarPlanes(limite);
    }

    /** Pagos de pedidos de las tiendas (Mercado Pago o PayPal). */
    void conciliarPedidos(OffsetDateTime limite) {
        List<Pago> pendientes = pagoRepository
                .findTop50ByEstadoAndFechaCreacionBeforeOrderByIdPagoAsc(Pago.PENDIENTE, limite);
        if (pendientes.isEmpty()) {
            return;     // lo normal: no se escribe nada en el log
        }

        int cerrados = 0;
        for (Pago pago : pendientes) {
            try {
                if (pagoService.conciliar(pago.getIdPago())) {
                    cerrados++;
                }
            } catch (RuntimeException e) {
                // Un pago que no se puede conciliar (la pasarela no responde, por ejemplo) no
                // interrumpe la revisión: se queda pendiente y se vuelve a intentar en 15 minutos.
                log.warn("No se pudo conciliar el pago {} del pedido {}: {}",
                        pago.getIdPago(), pago.getIdPedido(), e.getMessage());
            }
        }
        log.info("Conciliación de pagos: {} pendiente(s) revisado(s), {} cerrado(s)", pendientes.size(), cerrados);
    }

    /** Pagos de plan de los dueños (siempre con PayPal). */
    void conciliarPlanes(OffsetDateTime limite) {
        List<Suscripcion> pendientes = suscripcionRepository
                .findTop50ByEstadoAndFechaCreacionBeforeOrderByIdSuscripcionAsc(Suscripcion.PENDIENTE, limite);
        if (pendientes.isEmpty()) {
            return;
        }

        int cerrados = 0;
        for (Suscripcion s : pendientes) {
            try {
                if (suscripcionService.conciliar(s.getIdSuscripcion())) {
                    cerrados++;
                }
            } catch (RuntimeException e) {
                // Igual que con los pedidos: si PayPal no responde, se reintenta en 15 minutos.
                log.warn("No se pudo conciliar el pago de plan {} de la tienda {}: {}",
                        s.getIdSuscripcion(), s.getIdTienda(), e.getMessage());
            }
        }
        log.info("Conciliación de planes: {} pendiente(s) revisado(s), {} cerrado(s)", pendientes.size(), cerrados);
    }
}
