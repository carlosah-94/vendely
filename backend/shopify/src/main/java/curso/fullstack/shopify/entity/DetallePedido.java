package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Una línea del pedido. Guarda el precio del momento (id_precio) y una foto del nombre. */
@Entity
@Table(name = "detalle_pedido")
@IdClass(DetallePedidoId.class)
@Getter
public class DetallePedido {

    @Setter
    @Id
    @Column(name = "id_pedido")
    private Long idPedido;

    @Setter
    @Id
    @Column(name = "id_variante")
    private Long idVariante;

    @Setter
    @Column(name = "id_precio", nullable = false)
    private Long idPrecio;

    @Setter
    @Column(name = "nombre_producto", nullable = false, length = 200)
    private String nombreProducto;

    @Setter
    @Column(name = "nombre_variante", nullable = false, length = 110)
    private String nombreVariante;

    @Setter
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "descuento_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoUnitario = BigDecimal.ZERO;
}
