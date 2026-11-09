package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Tabla "pedidos": una venta. El trigger T2 pone número, moneda, envío, total e impuesto. */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long idPedido;

    @Column(name = "id_tienda", nullable = false, updatable = false)
    private Long idTienda;

    @Column(name = "id_login")
    private Long idLogin;

    @Column(name = "id_direccion_envio", nullable = false)
    private Long idDireccionEnvio;

    @Column(name = "correo_contacto", nullable = false, length = 150)
    private String correoContacto;

    /** Lo calcula Java: suma de precio × cantidad de las líneas. */
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

    public Long getIdPedido() { return idPedido; }
    public Long getIdTienda() { return idTienda; }
    public void setIdTienda(Long idTienda) { this.idTienda = idTienda; }
    public Long getIdLogin() { return idLogin; }
    public void setIdLogin(Long idLogin) { this.idLogin = idLogin; }
    public Long getIdDireccionEnvio() { return idDireccionEnvio; }
    public void setIdDireccionEnvio(Long idDireccionEnvio) { this.idDireccionEnvio = idDireccionEnvio; }
    public String getCorreoContacto() { return correoContacto; }
    public void setCorreoContacto(String correoContacto) { this.correoContacto = correoContacto; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public Short getEstado() { return estado; }
    public Integer getNumeroPedido() { return numeroPedido; }
    public String getMoneda() { return moneda; }
    public Short getIdImpuesto() { return idImpuesto; }
    public BigDecimal getCostoEnvio() { return costoEnvio; }
    public BigDecimal getMontoImpuestos() { return montoImpuestos; }
    public BigDecimal getTotal() { return total; }
    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
}