package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "notificaciones": cada correo que el sistema intentó enviar y cómo terminó.
 *
 * Se guarda el intento aunque el envío falle. Así el dueño ve en el detalle de la venta qué
 * correos salieron, y un problema del proveedor de correo no deja al pedido a medias ni se
 * pierde sin rastro.
 */
@Entity
@Table(name = "notificaciones")
@Getter
@Setter
public class Notificacion {

    // ---- tipo: a quién y por qué se le escribe
    public static final short PAGO_APROBADO = 1;    // al cliente: su compra se confirmó
    public static final short NUEVO_PEDIDO = 2;     // al dueño: le entró una venta
    public static final short PEDIDO_ENVIADO = 3;   // al cliente: con empresa y número de seguimiento
    public static final short STOCK_BAJO = 4;       // al dueño: a un producto le queda poco

    // ---- estado del intento
    public static final short ENVIADO = 1;
    public static final short FALLIDO = 2;
    public static final short SIMULADO = 3;         // sin RESEND_API_KEY: se registra sin enviar

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long idNotificacion;

    @Column(name = "id_tienda", nullable = false, updatable = false)
    private Long idTienda;

    /** NULL en los avisos que no son de un pedido (el de stock bajo). */
    @Column(name = "id_pedido", updatable = false)
    private Long idPedido;

    @Column(name = "destinatario", nullable = false, length = 150)
    private String destinatario;

    @Column(name = "tipo", nullable = false)
    private Short tipo;

    @Column(name = "asunto", nullable = false, length = 200)
    private String asunto;

    @Column(name = "estado", nullable = false)
    private Short estado;

    /** El motivo, solo cuando falló (lo exige ck_notificaciones_error). */
    @Column(name = "error")
    private String error;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    /** Cuándo salió. NULL si falló (lo exige ck_notificaciones_envio). */
    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;
}
