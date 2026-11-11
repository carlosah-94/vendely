package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "pagos": cada intento de pago de un pedido.
 * Solo puede haber UN pago aprobado por pedido (índice uq_pago_aprobado) y la referencia
 * de la pasarela es única: el mismo pago (de Mercado Pago o de PayPal) no sirve para dos pedidos.
 */
@Entity
@Table(name = "pagos")
@Getter
@Setter
public class Pago {

    public static final short PAYPAL = 2;
    public static final short MERCADO_PAGO = 3;
    public static final short PENDIENTE = 0;
    public static final short APROBADO = 1;
    public static final short RECHAZADO = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @Column(name = "id_pedido", nullable = false, updatable = false)
    private Long idPedido;

    /** 1 Culqi · 2 PayPal · 3 Mercado Pago · 4 manual · 5 Stripe */
    @Column(name = "proveedor", nullable = false)
    private Short proveedor = MERCADO_PAGO;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** 0 pendiente · 1 aprobado · 2 rechazado · 3 reembolsado */
    @Column(name = "estado", nullable = false)
    private Short estado = PENDIENTE;

    /** Código de la pasarela: el paymentId de Mercado Pago o el id de la orden de PayPal. */
    @Column(name = "referencia_transaccion", length = 120)
    private String referenciaTransaccion;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_pago")
    private OffsetDateTime fechaPago;
}
