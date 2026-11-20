package curso.fullstack.shopify.evento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Anota cada evento en el log para comprobar que se publican.
 * AFTER_COMMIT (por defecto): solo se escucha si la transaccion se guardo de verdad.
 * Si la venta falla y se deshace, no se avisa de un pedido que no existe.
 */
@Slf4j
@Component
public class EventoLogListener {

    @TransactionalEventListener(fallbackExecution = true)
    public void pedidoCreado(PedidoCreadoEvent e) {
        log.info("Evento PedidoCreado: tienda={}, pedido={}", e.idTienda(), e.idPedido());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void pagoAprobado(PagoAprobadoEvent e) {
        log.info("Evento PagoAprobado: tienda={}, pedido={}", e.idTienda(), e.idPedido());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void estadoCambiado(PedidoEstadoCambiadoEvent e) {
        log.info("Evento PedidoEstadoCambiado: tienda={}, pedido={}, {} -> {}",
                e.idTienda(), e.idPedido(), e.estadoAnterior(), e.estadoNuevo());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void stockCambiado(StockCambiadoEvent e) {
        log.info("Evento StockCambiado: tienda={}, variante={}, quedan={}", e.idTienda(), e.idVariante(), e.cantidad());
    }
}
