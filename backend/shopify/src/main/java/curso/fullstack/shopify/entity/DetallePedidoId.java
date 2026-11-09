package curso.fullstack.shopify.entity;

import java.io.Serializable;
import java.util.Objects;

/** Llave de detalle_pedido: (id_pedido, id_variante). */
public class DetallePedidoId implements Serializable {
    private Long idPedido;
    private Long idVariante;

    public DetallePedidoId() {}
    public DetallePedidoId(Long idPedido, Long idVariante) { this.idPedido = idPedido; this.idVariante = idVariante; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetallePedidoId d)) return false;
        return Objects.equals(idPedido, d.idPedido) && Objects.equals(idVariante, d.idVariante);
    }
    @Override public int hashCode() { return Objects.hash(idPedido, idVariante); }
}