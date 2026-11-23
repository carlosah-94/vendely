package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Notificacion;
import java.time.OffsetDateTime;

/**
 * Un correo del pedido, como lo ve el dueño en el detalle de la venta (issue #3).
 * El tipo y el estado se envían como texto: la base los guarda como números y el frontend
 * no debería tener que conocer esa tabla de equivalencias.
 */
public record NotificacionResponse(Long idNotificacion, String tipo, String destinatario, String asunto,
                                   String estado, String error,
                                   OffsetDateTime fechaCreacion, OffsetDateTime fechaEnvio) {

    public static NotificacionResponse desde(Notificacion n) {
        return new NotificacionResponse(n.getIdNotificacion(), nombreTipo(n.getTipo()), n.getDestinatario(),
                n.getAsunto(), nombreEstado(n.getEstado()), n.getError(), n.getFechaCreacion(), n.getFechaEnvio());
    }

    public static String nombreTipo(Short tipo) {
        if (tipo == null) {
            return "DESCONOCIDO";
        }
        return switch (tipo) {
            case Notificacion.PAGO_APROBADO -> "PAGO_APROBADO";
            case Notificacion.NUEVO_PEDIDO -> "NUEVO_PEDIDO";
            case Notificacion.PEDIDO_ENVIADO -> "PEDIDO_ENVIADO";
            case Notificacion.STOCK_BAJO -> "STOCK_BAJO";
            default -> "DESCONOCIDO";
        };
    }

    /** SIMULADO = se registró sin enviar, porque no hay RESEND_API_KEY configurada. */
    public static String nombreEstado(Short estado) {
        if (estado == null) {
            return "DESCONOCIDO";
        }
        return switch (estado) {
            case Notificacion.ENVIADO -> "ENVIADO";
            case Notificacion.FALLIDO -> "FALLIDO";
            case Notificacion.SIMULADO -> "SIMULADO";
            default -> "DESCONOCIDO";
        };
    }
}
