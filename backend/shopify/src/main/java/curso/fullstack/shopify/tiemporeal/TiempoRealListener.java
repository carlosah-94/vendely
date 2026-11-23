package curso.fullstack.shopify.tiemporeal;

import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.entity.Pedido;
import curso.fullstack.shopify.evento.PagoAprobadoEvent;
import curso.fullstack.shopify.evento.PedidoCreadoEvent;
import curso.fullstack.shopify.evento.PedidoEstadoCambiadoEvent;
import curso.fullstack.shopify.evento.StockCambiadoEvent;
import curso.fullstack.shopify.repository.PedidoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Convierte los cuatro eventos del negocio en avisos de tiempo real (issue #4).
 *
 * Igual que con los correos, PedidoService y PagoService no saben que esto existe: publican lo
 * que pasó y aquí se decide a qué canal va. @TransactionalEventListener (AFTER_COMMIT): un pedido
 * que se deshace a mitad no aparece en el panel del dueño. fallbackExecution = true: si algún
 * evento se publicara fuera de una transacción, igual se avisa.
 *
 * Nunca lanza: si un aviso no sale, la venta ya está guardada. Lo peor que pasa es que la
 * pantalla se actualiza al recargar, como antes.
 */
@Slf4j
@Component
public class TiempoRealListener {

    private final SimpMessagingTemplate mensajes;
    private final PedidoRepository pedidoRepository;

    public TiempoRealListener(SimpMessagingTemplate mensajes, PedidoRepository pedidoRepository) {
        this.mensajes = mensajes;
        this.pedidoRepository = pedidoRepository;
    }

    /** Pedido nuevo: la campanita del dueño. */
    @TransactionalEventListener(fallbackExecution = true)
    public void pedidoCreado(PedidoCreadoEvent e) {
        pedidoRepository.findById(e.idPedido()).ifPresent(p ->
                enviar(Canales.pedidos(e.idTienda()), aviso("PEDIDO_CREADO", p)));
    }

    /** Pago aprobado: al dueño (cambia la etiqueta en su lista) y al cliente (su pedido). */
    @TransactionalEventListener(fallbackExecution = true)
    public void pagoAprobado(PagoAprobadoEvent e) {
        pedidoRepository.findById(e.idPedido()).ifPresent(p -> {
            AvisoTiempoReal aviso = aviso("PAGO_APROBADO", p);
            enviar(Canales.pedidos(e.idTienda()), aviso);
            alCliente(p, aviso);
        });
    }

    /** Enviado, entregado o cancelado: el cliente ve su línea de tiempo moverse sola. */
    @TransactionalEventListener(fallbackExecution = true)
    public void estadoCambiado(PedidoEstadoCambiadoEvent e) {
        pedidoRepository.findById(e.idPedido()).ifPresent(p -> {
            AvisoTiempoReal aviso = aviso("ESTADO_CAMBIADO", p);
            enviar(Canales.pedidos(e.idTienda()), aviso);
            alCliente(p, aviso);
        });
    }

    /**
     * Stock: al panel (Inventario se actualiza) y a la tienda pública (el "Agotado" aparece
     * sin recargar). Al canal público va solo la variante y la cantidad, que ya son públicas.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void stockCambiado(StockCambiadoEvent e) {
        AvisoTiempoReal aviso = AvisoTiempoReal.stock(e.idVariante(), e.cantidad());
        enviar(Canales.inventario(e.idTienda()), aviso);
        enviar(Canales.stock(e.idTienda()), aviso);
    }

    // ------------------------------------------------------------------ apoyo

    private static AvisoTiempoReal aviso(String tipo, Pedido p) {
        return AvisoTiempoReal.pedido(tipo, p.getIdPedido(), p.getNumeroPedido(),
                PedidoResumenResponse.nombreEstado(p.getEstado()), p.getTotal(), p.getMoneda());
    }

    /** Solo si el pedido es de un cliente con cuenta: un invitado no tiene conexión a su nombre. */
    private void alCliente(Pedido p, AvisoTiempoReal aviso) {
        if (p.getIdLogin() == null) {
            return;
        }
        try {
            mensajes.convertAndSendToUser(String.valueOf(p.getIdLogin()), Canales.MIS_PEDIDOS, aviso);
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar en tiempo real al cliente {}: {}", p.getIdLogin(), ex.getMessage());
        }
    }

    private void enviar(String canal, AvisoTiempoReal aviso) {
        try {
            mensajes.convertAndSend(canal, aviso);
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar el aviso {} a {}: {}", aviso.tipo(), canal, ex.getMessage());
        }
    }
}
