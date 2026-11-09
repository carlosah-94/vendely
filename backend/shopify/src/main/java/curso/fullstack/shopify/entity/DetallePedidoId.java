package curso.fullstack.shopify.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Llave de detalle_pedido: (id_pedido, id_variante). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DetallePedidoId implements Serializable {
    private Long idPedido;
    private Long idVariante;
}
