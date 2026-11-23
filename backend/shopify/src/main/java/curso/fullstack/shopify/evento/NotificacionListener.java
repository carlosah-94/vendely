package curso.fullstack.shopify.evento;

import curso.fullstack.shopify.service.NotificacionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Convierte los eventos del negocio en correos (issue #3).
 *
 * Está separado de los servicios a propósito: PedidoService y PagoService no tienen que saber
 * que existe el correo. Publican lo que pasó y aquí se decide a quién se le avisa. Si mañana
 * hay que agregar un aviso por WhatsApp, se agrega otro oyente y no se toca la venta.
 *
 * @TransactionalEventListener (AFTER_COMMIT, el valor por defecto): el correo se manda solo si
 * la transacción se guardó de verdad. Sin eso, un pago que se deshace a mitad igual enviaría
 * la confirmación, y el cliente tendría un correo de una compra que no existe.
 */
@Slf4j
@Component
public class NotificacionListener {

    private final NotificacionService notificaciones;

    public NotificacionListener(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    /** Pago aprobado: confirmación al cliente (con el comprobante) y aviso al dueño. */
    @TransactionalEventListener
    public void pagoAprobado(PagoAprobadoEvent e) {
        notificaciones.avisarPagoAprobado(e.idTienda(), e.idPedido());
    }

    /** Solo el paso a ENVIADO genera correo; entregado y cancelado se ven en el panel. */
    @TransactionalEventListener
    public void estadoCambiado(PedidoEstadoCambiadoEvent e) {
        if (e.estadoNuevo() != null && e.estadoNuevo() == 1) {
            notificaciones.avisarPedidoEnviado(e.idTienda(), e.idPedido());
        }
    }

    /**
     * Stock bajo. NotificacionService decide si avisar: solo cuando lo que queda llegó al
     * mínimo configurado de esa variante, así una venta normal no llena de correos al dueño.
     */
    @TransactionalEventListener
    public void stockCambiado(StockCambiadoEvent e) {
        notificaciones.avisarStockBajo(e.idTienda(), e.idVariante(), e.cantidad());
    }
}
