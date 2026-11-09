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

    /** 0 sin enviar · 1 enviado · 2 entregado · 3 cancelado */
    @Column(name = "estado", nullable = false)
    private Short estado = 0;

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
}
