package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Una línea del pedido. Guarda el precio del momento (id_precio) y una foto del nombre. */
@Entity
@Table(name = "detalle_pedido")
@IdClass(DetallePedidoId.class)
public class DetallePedido {

    @Id
    @Column(name = "id_pedido")
    private Long idPedido;

    @Id
    @Column(name = "id_variante")
    private Long idVariante;

    @Column(name = "id_precio", nullable = false)
    private Long idPrecio;

    @Column(name = "nombre_producto", nullable = false, length = 200)
    private String nombreProducto;

    @Column(name = "nombre_variante", nullable = false, length = 110)
    private String nombreVariante;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "descuento_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoUnitario = BigDecimal.ZERO;

    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public Long getIdVariante() { return idVariante; }
    public void setIdVariante(Long idVariante) { this.idVariante = idVariante; }
    public Long getIdPrecio() { return idPrecio; }
    public void setIdPrecio(Long idPrecio) { this.idPrecio = idPrecio; }
    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }
    public String getNombreVariante() { return nombreVariante; }
    public void setNombreVariante(String nombreVariante) { this.nombreVariante = nombreVariante; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getDescuentoUnitario() { return descuentoUnitario; }
}