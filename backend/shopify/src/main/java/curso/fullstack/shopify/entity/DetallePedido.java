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

    /** Oferta con la que se vendió (null = sin oferta). */
    @Setter
    @Column(name = "id_oferta")
    private Long idOferta;

    /** Lo que rebajó la oferta por unidad (0 sin oferta). Lo revisa el trigger T7. */
    @Setter
    @Column(name = "descuento_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoUnitario = BigDecimal.ZERO;

    /** Lo que respondió el cliente a la pregunta del producto (V19), o null. */
    @Setter
    @Column(name = "dato_cliente", length = 300)
    private String datoCliente;
}
