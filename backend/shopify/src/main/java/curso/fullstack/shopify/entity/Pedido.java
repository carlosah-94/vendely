package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** Tabla "pedidos": una venta. El trigger T2 pone número, moneda, envío, total e impuesto. */
@Entity
@Table(name = "pedidos")
@Getter
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long idPedido;

    @Setter
    @Column(name = "id_tienda", nullable = false, updatable = false)
    private Long idTienda;

    @Setter
    @Column(name = "id_login")
    private Long idLogin;

    @Setter
    @Column(name = "id_direccion_envio", nullable = false)
    private Long idDireccionEnvio;

    @Setter
    @Column(name = "correo_contacto", nullable = false, length = 150)
    private String correoContacto;

    /** Lo calcula Java: suma de precio × cantidad de las líneas. */
    @Setter
    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /** 0 sin enviar · 1 enviado · 2 entregado · 3 cancelado. Lo cambia PedidoService. */
    @Setter
    @Column(name = "estado", nullable = false)
    private Short estado = 0;

    // ---- Seguimiento del envio (issue #3). Las fechas marcan cada paso del pedido.
    @Setter
    @Column(name = "empresa_envio", length = 60)
    private String empresaEnvio;

    @Setter
    @Column(name = "numero_seguimiento", length = 60)
    private String numeroSeguimiento;

    @Setter
    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Setter
    @Column(name = "fecha_entrega")
    private OffsetDateTime fechaEntrega;

    @Setter
    @Column(name = "fecha_cancelacion")
    private OffsetDateTime fechaCancelacion;

    /** Lo que Vendely cobra por esta venta. Se guarda al aprobarse el pago, con el plan de ese momento. */
    @Setter
    @Column(name = "comision_plataforma", precision = 12, scale = 2)
    private BigDecimal comisionPlataforma;

    // ---- Las llena la base (trigger T2). Java solo las lee.
    @Column(name = "numero_pedido", insertable = false, updatable = false)
    private Integer numeroPedido;

    @Column(name = "moneda", columnDefinition = "bpchar(3)", insertable = false, updatable = false)
    private String moneda;

    @Column(name = "id_impuesto", insertable = false, updatable = false)
    private Short idImpuesto;

    @Column(name = "costo_envio", precision = 12, scale = 2, insertable = false, updatable = false)
    private BigDecimal costoEnvio;

    @Column(name = "monto_impuestos", precision = 12, scale = 2, insertable = false, updatable = false)
    private BigDecimal montoImpuestos;

    @Column(name = "total", precision = 12, scale = 2, insertable = false, updatable = false)
    private BigDecimal total;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    /** Código de descuento usado (V14), o null. Se fija al crear el pedido y no cambia. */
    @Setter
    @Column(name = "id_cupon", updatable = false)
    private Long idCupon;

    /** Lo que rebajó el código: total = subtotal - descuentoCupon + costoEnvio. */
    @Setter
    @Column(name = "descuento_cupon", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal descuentoCupon = BigDecimal.ZERO;
}
